package com.Wealthify.backend.service;

import com.Wealthify.backend.entity.Goal;
import com.Wealthify.backend.entity.User;
import com.Wealthify.backend.repository.GoalRepository;
import com.Wealthify.backend.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * DATA-02 Concurrency Integration Test Suite:
 * Verifies pessimistic locking (@Lock(PESSIMISTIC_WRITE) / findByIdForUpdate) and
 * @Transactional boundary in GoalService.updateGoalSaving to prevent lost updates under race conditions.
 */
@SpringBootTest
@ActiveProfiles("local")
class GoalConcurrencyIntegrationTest {

    @Autowired
    private GoalService goalService;

    @Autowired
    private GoalRepository goalRepository;

    @Autowired
    private UserRepository userRepository;

    private final List<UUID> createdGoalIds = new ArrayList<>();
    private final List<UUID> createdUserIds = new ArrayList<>();

    private User testUser;
    private Goal testGoal;

    @BeforeEach
    void setUp() {
        testUser = userRepository.save(User.builder()
                .name("Concurrency Tester")
                .email("concurrency_tester_" + UUID.randomUUID() + "@example.com")
                .password("$2a$10$dummyHashedPasswordForTestExecutionOnly")
                .monthlyIncome(BigDecimal.valueOf(100000.00))
                .savingPercentage(BigDecimal.valueOf(25.0))
                .build());
        createdUserIds.add(testUser.getId());

        testGoal = goalRepository.save(Goal.builder()
                .user(testUser)
                .itemName("Emergency Fund")
                .targetAmount(BigDecimal.valueOf(50000.00))
                .targetDate(LocalDate.now().plusYears(1))
                .currentSaved(BigDecimal.ZERO)
                .status("ACTIVE")
                .build());
        createdGoalIds.add(testGoal.getId());
    }

    @AfterEach
    void tearDown() {
        for (UUID goalId : createdGoalIds) {
            try {
                goalRepository.deleteById(goalId);
            } catch (Exception ignored) {
            }
        }
        createdGoalIds.clear();

        for (UUID userId : createdUserIds) {
            try {
                userRepository.deleteById(userId);
            } catch (Exception ignored) {
            }
        }
        createdUserIds.clear();
    }

    @Test
    @DisplayName("DATA-02 Test 1: 10 concurrent threads adding ₹100 to same goal results in exactly ₹1,000 (no lost updates)")
    void testConcurrentGoalSavingsNoLostUpdates() throws InterruptedException {
        int threadCount = 10;
        BigDecimal incrementAmount = BigDecimal.valueOf(100.00);
        BigDecimal expectedFinalBalance = BigDecimal.valueOf(1000.00);

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        List<Throwable> exceptions = Collections.synchronizedList(new ArrayList<>());

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await(); // wait for simultaneous trigger
                    goalService.updateGoalSaving(testUser.getEmail(), testGoal.getId(), incrementAmount);
                    successCount.incrementAndGet();
                } catch (Throwable t) {
                    exceptions.add(t);
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        // Release all threads simultaneously
        startLatch.countDown();
        boolean completed = doneLatch.await(30, TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(completed).as("All concurrent tasks should complete within timeout").isTrue();
        assertThat(exceptions).as("No exceptions should occur during concurrent updates").isEmpty();
        assertThat(successCount.get()).isEqualTo(threadCount);

        // Fetch fresh state from database to verify persisted consistency
        Goal freshGoal = goalRepository.findById(testGoal.getId()).orElseThrow();
        assertThat(freshGoal.getCurrentSaved())
                .as("Final balance must reflect all 10 increments with zero lost updates")
                .isEqualByComparingTo(expectedFinalBalance);
        assertThat(freshGoal.getStatus()).isEqualTo("ACTIVE");
    }

    @Test
    @DisplayName("DATA-02 Test 2: Concurrent savings crossing target threshold safely transitions status to ACHIEVED")
    void testConcurrentSavingsTransitionsToAchieved() throws InterruptedException {
        // Goal target is ₹1,000, initial saved is ₹600.
        // 5 concurrent threads each adding ₹100 -> total saved should be ₹1,100 and status ACHIEVED.
        Goal thresholdGoal = goalRepository.save(Goal.builder()
                .user(testUser)
                .itemName("Laptop Upgrade")
                .targetAmount(BigDecimal.valueOf(1000.00))
                .targetDate(LocalDate.now().plusMonths(6))
                .currentSaved(BigDecimal.valueOf(600.00))
                .status("ACTIVE")
                .build());
        createdGoalIds.add(thresholdGoal.getId());

        int threadCount = 5;
        BigDecimal incrementAmount = BigDecimal.valueOf(100.00);
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        List<Throwable> exceptions = Collections.synchronizedList(new ArrayList<>());

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    goalService.updateGoalSaving(testUser.getEmail(), thresholdGoal.getId(), incrementAmount);
                } catch (Throwable t) {
                    exceptions.add(t);
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        boolean completed = doneLatch.await(30, TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(completed).isTrue();
        assertThat(exceptions).isEmpty();

        Goal freshGoal = goalRepository.findById(thresholdGoal.getId()).orElseThrow();
        assertThat(freshGoal.getCurrentSaved()).isEqualByComparingTo(BigDecimal.valueOf(1100.00));
        assertThat(freshGoal.getStatus()).isEqualTo("ACHIEVED");
    }

    @Test
    @DisplayName("DATA-02 Test 3: Concurrent savings to independent goals execute without cross-talk or blocking")
    void testConcurrentSavingsIndependentGoals() throws InterruptedException {
        User secondUser = userRepository.save(User.builder()
                .name("Second User")
                .email("second_user_" + UUID.randomUUID() + "@example.com")
                .password("$2a$10$dummyHashedPasswordForTestExecutionOnly")
                .monthlyIncome(BigDecimal.valueOf(80000.00))
                .savingPercentage(BigDecimal.valueOf(20.0))
                .build());
        createdUserIds.add(secondUser.getId());

        Goal secondGoal = goalRepository.save(Goal.builder()
                .user(secondUser)
                .itemName("Vacation Trip")
                .targetAmount(BigDecimal.valueOf(20000.00))
                .targetDate(LocalDate.now().plusMonths(8))
                .currentSaved(BigDecimal.ZERO)
                .status("ACTIVE")
                .build());
        createdGoalIds.add(secondGoal.getId());

        int threadsPerGoal = 5;
        int totalThreads = threadsPerGoal * 2;
        ExecutorService executor = Executors.newFixedThreadPool(totalThreads);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(totalThreads);
        List<Throwable> exceptions = Collections.synchronizedList(new ArrayList<>());

        for (int i = 0; i < threadsPerGoal; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    goalService.updateGoalSaving(testUser.getEmail(), testGoal.getId(), BigDecimal.valueOf(100.00));
                } catch (Throwable t) {
                    exceptions.add(t);
                } finally {
                    doneLatch.countDown();
                }
            });
            executor.submit(() -> {
                try {
                    startLatch.await();
                    goalService.updateGoalSaving(secondUser.getEmail(), secondGoal.getId(), BigDecimal.valueOf(200.00));
                } catch (Throwable t) {
                    exceptions.add(t);
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        boolean completed = doneLatch.await(30, TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(completed).isTrue();
        assertThat(exceptions).isEmpty();

        Goal freshGoal1 = goalRepository.findById(testGoal.getId()).orElseThrow();
        Goal freshGoal2 = goalRepository.findById(secondGoal.getId()).orElseThrow();

        assertThat(freshGoal1.getCurrentSaved()).isEqualByComparingTo(BigDecimal.valueOf(500.00));
        assertThat(freshGoal2.getCurrentSaved()).isEqualByComparingTo(BigDecimal.valueOf(1000.00));
    }
}
