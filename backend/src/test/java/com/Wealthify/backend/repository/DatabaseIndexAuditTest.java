package com.Wealthify.backend.repository;

import com.Wealthify.backend.entity.Expense;
import com.Wealthify.backend.entity.Goal;
import com.Wealthify.backend.entity.User;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("local")
class DatabaseIndexAuditTest {

    @Autowired
    private ExpenseRepository expenseRepository;

    @Autowired
    private GoalRepository goalRepository;

    @Autowired
    private UserRepository userRepository;

    private final List<UUID> createdExpenseIds = new ArrayList<>();
    private final List<UUID> createdGoalIds = new ArrayList<>();
    private final List<UUID> createdUserIds = new ArrayList<>();

    private User userA;
    private User userB;

    @BeforeEach
    void setUp() {
        userA = User.builder()
                .name("Alice IndexTest")
                .email("alice.index." + UUID.randomUUID() + "@example.com")
                .password("encoded_pass_123")
                .monthlyIncome(BigDecimal.valueOf(75000))
                .build();
        userA = userRepository.save(userA);
        createdUserIds.add(userA.getId());

        userB = User.builder()
                .name("Bob IndexTest")
                .email("bob.index." + UUID.randomUUID() + "@example.com")
                .password("encoded_pass_123")
                .monthlyIncome(BigDecimal.valueOf(60000))
                .build();
        userB = userRepository.save(userB);
        createdUserIds.add(userB.getId());
    }

    @AfterEach
    void tearDown() {
        for (UUID expenseId : createdExpenseIds) {
            expenseRepository.findById(expenseId).ifPresent(expenseRepository::delete);
        }
        for (UUID goalId : createdGoalIds) {
            goalRepository.findById(goalId).ifPresent(goalRepository::delete);
        }
        for (UUID userId : createdUserIds) {
            userRepository.findById(userId).ifPresent(userRepository::delete);
        }
    }

    // ─── Test 1: Index Metadata Declarations ────────────────────────────────────

    @Test
    @DisplayName("DATA-01 Test 1: JPA entity metadata declares intended composite indexes")
    void testIndexMetadataDeclaredOnEntities() {
        // Verify Expense entity indexes
        Table expenseTable = Expense.class.getAnnotation(Table.class);
        assertThat(expenseTable).isNotNull();
        assertThat(expenseTable.name()).isEqualTo("expenses");

        Index[] expenseIndexes = expenseTable.indexes();
        assertThat(expenseIndexes).isNotEmpty();

        boolean hasExpenseUserDateIndex = Arrays.stream(expenseIndexes)
                .anyMatch(idx -> "idx_expenses_user_date".equals(idx.name())
                        && "user_id, expense_date".equals(idx.columnList()));
        assertThat(hasExpenseUserDateIndex)
                .as("Expense must declare composite index idx_expenses_user_date on (user_id, expense_date)")
                .isTrue();

        // Verify Goal entity indexes
        Table goalTable = Goal.class.getAnnotation(Table.class);
        assertThat(goalTable).isNotNull();
        assertThat(goalTable.name()).isEqualTo("goals");

        Index[] goalIndexes = goalTable.indexes();
        assertThat(goalIndexes).isNotEmpty();

        boolean hasGoalUserCreatedIndex = Arrays.stream(goalIndexes)
                .anyMatch(idx -> "idx_goals_user_created_at".equals(idx.name())
                        && "user_id, created_at".equals(idx.columnList()));
        assertThat(hasGoalUserCreatedIndex)
                .as("Goal must declare composite index idx_goals_user_created_at on (user_id, created_at)")
                .isTrue();
    }

    // ─── Test 2: Existing Repository Behavior ───────────────────────────────────

    @Test
    @DisplayName("DATA-01 Test 2: User and date range query returns correct records sorted descending")
    void testExpenseDateRangeQueryReturnsCorrectSortedResults() {
        LocalDate d1 = LocalDate.of(2026, 10, 1);
        LocalDate d2 = LocalDate.of(2026, 10, 2);
        LocalDate d3 = LocalDate.of(2026, 10, 3);
        LocalDate d4 = LocalDate.of(2026, 10, 4);

        Expense e1 = createExpense(userA, BigDecimal.valueOf(100), "Day 1", d1);
        Expense e2 = createExpense(userA, BigDecimal.valueOf(200), "Day 2", d2);
        Expense e3 = createExpense(userA, BigDecimal.valueOf(300), "Day 3", d3);
        Expense e4 = createExpense(userA, BigDecimal.valueOf(400), "Day 4", d4);

        // Query between d2 and d4 (should include d2, d3, d4 and exclude d1)
        List<Expense> results = expenseRepository.findByUserAndExpenseDateBetweenOrderByExpenseDateDesc(userA, d2, d4);

        assertThat(results).hasSize(3);
        assertThat(results.get(0).getId()).isEqualTo(e4.getId());
        assertThat(results.get(1).getId()).isEqualTo(e3.getId());
        assertThat(results.get(2).getId()).isEqualTo(e2.getId());

        // Verify ordering is strictly descending by expenseDate
        assertThat(results.get(0).getExpenseDate()).isEqualTo(d4);
        assertThat(results.get(1).getExpenseDate()).isEqualTo(d3);
        assertThat(results.get(2).getExpenseDate()).isEqualTo(d2);

        // Verify full user listing ordered by expenseDate DESC
        List<Expense> allResults = expenseRepository.findByUserOrderByExpenseDateDesc(userA);
        assertThat(allResults).hasSize(4);
        assertThat(allResults.get(0).getId()).isEqualTo(e4.getId());
        assertThat(allResults.get(3).getId()).isEqualTo(e1.getId());
    }

    @Test
    @DisplayName("DATA-01 Test 2b: Goal query returns correct user goals sorted by createdAt descending")
    void testGoalCreatedOrderQueryReturnsCorrectSortedResults() {
        Goal g1 = createGoal(userA, "Emergency Fund", BigDecimal.valueOf(100000), LocalDateTime.now().minusDays(3));
        Goal g2 = createGoal(userA, "Vacation", BigDecimal.valueOf(50000), LocalDateTime.now().minusDays(2));
        Goal g3 = createGoal(userA, "Laptop", BigDecimal.valueOf(80000), LocalDateTime.now().minusDays(1));

        List<Goal> goals = goalRepository.findByUserOrderByCreatedAtDesc(userA);

        assertThat(goals).hasSize(3);
        assertThat(goals.get(0).getId()).isEqualTo(g3.getId());
        assertThat(goals.get(1).getId()).isEqualTo(g2.getId());
        assertThat(goals.get(2).getId()).isEqualTo(g1.getId());
    }

    // ─── Test 3: Multi-User Isolation ───────────────────────────────────────────

    @Test
    @DisplayName("DATA-01 Test 3: Indexed queries preserve strict multi-user isolation")
    void testMultiUserIsolationPreserved() {
        LocalDate targetDate = LocalDate.of(2026, 10, 4);

        Expense eA1 = createExpense(userA, BigDecimal.valueOf(150), "User A Item 1", targetDate);
        Expense eA2 = createExpense(userA, BigDecimal.valueOf(250), "User A Item 2", targetDate);

        Expense eB1 = createExpense(userB, BigDecimal.valueOf(999), "User B Private Item", targetDate);

        Goal gA = createGoal(userA, "User A Goal", BigDecimal.valueOf(50000), LocalDateTime.now());
        Goal gB = createGoal(userB, "User B Secret Goal", BigDecimal.valueOf(999999), LocalDateTime.now());

        // Query expenses for User A
        List<Expense> userAExpenses = expenseRepository
                .findByUserAndExpenseDateBetweenOrderByExpenseDateDesc(userA, targetDate, targetDate);

        assertThat(userAExpenses).hasSize(2);
        assertThat(userAExpenses)
                .extracting(Expense::getId)
                .containsExactlyInAnyOrder(eA1.getId(), eA2.getId())
                .doesNotContain(eB1.getId());

        // Query expenses for User B
        List<Expense> userBExpenses = expenseRepository
                .findByUserAndExpenseDateBetweenOrderByExpenseDateDesc(userB, targetDate, targetDate);

        assertThat(userBExpenses).hasSize(1);
        assertThat(userBExpenses.get(0).getId()).isEqualTo(eB1.getId());

        // Query goals for User A
        List<Goal> userAGoals = goalRepository.findByUserOrderByCreatedAtDesc(userA);
        assertThat(userAGoals)
                .extracting(Goal::getId)
                .contains(gA.getId())
                .doesNotContain(gB.getId());

        // Query goals for User B
        List<Goal> userBGoals = goalRepository.findByUserOrderByCreatedAtDesc(userB);
        assertThat(userBGoals)
                .extracting(Goal::getId)
                .contains(gB.getId())
                .doesNotContain(gA.getId());
    }

    // ─── Test 4: Database Initialization / Test Startup ─────────────────────────

    @Test
    @DisplayName("DATA-01 Test 4: Spring application context and JPA schema startup succeed cleanly")
    void testDatabaseStartupSucceedsWithIndexes() {
        assertThat(expenseRepository).isNotNull();
        assertThat(goalRepository).isNotNull();
        assertThat(userRepository).isNotNull();
    }

    // ─── Helper Methods ─────────────────────────────────────────────────────────

    private Expense createExpense(User user, BigDecimal amount, String description, LocalDate date) {
        Expense expense = Expense.builder()
                .user(user)
                .amount(amount)
                .description(description)
                .expenseDate(date)
                .createdAt(LocalDateTime.now())
                .build();
        expense = expenseRepository.save(expense);
        createdExpenseIds.add(expense.getId());
        return expense;
    }

    private Goal createGoal(User user, String itemName, BigDecimal targetAmount, LocalDateTime createdAt) {
        Goal goal = Goal.builder()
                .user(user)
                .itemName(itemName)
                .targetAmount(targetAmount)
                .targetDate(LocalDate.now().plusMonths(6))
                .currentSaved(BigDecimal.ZERO)
                .status("ACTIVE")
                .createdAt(createdAt)
                .build();
        goal = goalRepository.save(goal);
        createdGoalIds.add(goal.getId());
        return goal;
    }
}
