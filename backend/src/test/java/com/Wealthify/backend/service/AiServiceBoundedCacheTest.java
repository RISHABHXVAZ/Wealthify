package com.Wealthify.backend.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * PERF-01 Regression Test Suite:
 * Verifies that AI in-memory caching is strictly bounded in capacity and TTL,
 * expired entries are cleaned without requiring exact-key access, and user isolation
 * remains completely preserved under concurrent load.
 */
@ExtendWith(MockitoExtension.class)
class AiServiceBoundedCacheTest {

    @Mock
    private RestTemplate restTemplate;

    private ObjectMapper objectMapper;
    private AiService aiService;

    private UUID userA;
    private UUID userB;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        aiService = new AiService(restTemplate, objectMapper);
        ReflectionTestUtils.setField(aiService, "apiKey", "test-key");
        ReflectionTestUtils.setField(aiService, "apiUrl", "https://api.groq.com/test");
        ReflectionTestUtils.setField(aiService, "model", "llama-test");

        userA = UUID.randomUUID();
        userB = UUID.randomUUID();
    }

    private ResponseEntity<String> mockGroqResponse(String text) {
        try {
            Map<String, Object> responseMap = Map.of(
                    "choices", List.of(
                            Map.of("message", Map.of("content", text))
                    )
            );
            return ResponseEntity.ok(objectMapper.writeValueAsString(responseMap));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    @DisplayName("PERF-01 Test 1: TTL eviction — entry expires after configured TTL")
    void testTtlEviction() {
        AtomicLong virtualNanoTime = new AtomicLong(System.nanoTime());
        aiService.setTicker(virtualNanoTime::get);

        when(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(String.class)))
                .thenReturn(mockGroqResponse("Advice Before TTL"))
                .thenReturn(mockGroqResponse("Advice After TTL"));

        // 1. Initial call populates cache
        String first = aiService.generateDailySummary(
                userA, LocalDate.of(2026, 10, 4), BigDecimal.valueOf(500), Map.of(), 0);
        assertThat(first).isEqualTo("Advice Before TTL");

        // 2. Call again before TTL expires — hits cache
        virtualNanoTime.addAndGet(TimeUnit.MINUTES.toNanos(5));
        String second = aiService.generateDailySummary(
                userA, LocalDate.of(2026, 10, 4), BigDecimal.valueOf(500), Map.of(), 0);
        assertThat(second).isEqualTo("Advice Before TTL");
        verify(restTemplate, times(1)).postForEntity(anyString(), any(HttpEntity.class), eq(String.class));

        // 3. Advance virtual time past the 10-minute TTL (total 12 minutes elapsed)
        virtualNanoTime.addAndGet(TimeUnit.MINUTES.toNanos(7));

        // 4. Request again — must detect expiration, evict, and invoke LLM for fresh advice
        String third = aiService.generateDailySummary(
                userA, LocalDate.of(2026, 10, 4), BigDecimal.valueOf(500), Map.of(), 0);
        assertThat(third).isEqualTo("Advice After TTL");
        verify(restTemplate, times(2)).postForEntity(anyString(), any(HttpEntity.class), eq(String.class));
    }

    @Test
    @DisplayName("PERF-01 Test 2: Expired entries are evicted without requiring exact-key access")
    void testExpiredEntriesEvictedWithoutExactKeyAccess() {
        AtomicLong virtualNanoTime = new AtomicLong(System.nanoTime());
        aiService.setTicker(virtualNanoTime::get);

        when(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(String.class)))
                .thenReturn(mockGroqResponse("Abandoned Entry"));

        // Generate an entry that will be abandoned (never queried again)
        aiService.generateDailySummary(
                userA, LocalDate.of(2026, 10, 4), BigDecimal.valueOf(999), Map.of(), 0);

        assertThat(aiService.getCacheSize()).isEqualTo(1);
        assertThat(aiService.getSummaryCache()).isNotEmpty();

        // Advance virtual time by 15 minutes past TTL
        virtualNanoTime.addAndGet(TimeUnit.MINUTES.toNanos(15));

        // Do NOT request the key again. Instead trigger cache maintenance/cleanup
        aiService.cleanUp();

        // Verify the abandoned expired entry has been completely purged from memory
        assertThat(aiService.getCacheSize()).isZero();
        assertThat(aiService.getSummaryCache()).isEmpty();
    }

    @Test
    @DisplayName("PERF-01 Test 3: Maximum capacity — cache size is strictly bounded to max-capacity")
    void testMaximumCapacityEviction() {
        // Configure small capacity of 3
        int configuredCapacity = 3;
        aiService.setMaxCapacity(configuredCapacity);

        when(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(String.class)))
                .thenAnswer(inv -> mockGroqResponse("Advice for " + UUID.randomUUID()));

        // Insert 6 distinct entries (double the capacity)
        for (int i = 1; i <= 6; i++) {
            aiService.generateDailySummary(
                    userA, LocalDate.of(2026, 10, i), BigDecimal.valueOf(100 * i), Map.of(), 0);
        }

        aiService.cleanUp();

        // Cache must never exceed configured capacity
        assertThat(aiService.getCacheSize()).isLessThanOrEqualTo(configuredCapacity);
        assertThat(aiService.getSummaryCache().size()).isLessThanOrEqualTo(configuredCapacity);
    }

    @Test
    @DisplayName("PERF-01 Test 4: Valid entries still hit cache without calling LLM")
    void testValidEntriesHitCache() {
        when(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(String.class)))
                .thenReturn(mockGroqResponse("Cached Budget Advice"));

        String res1 = aiService.generateBudgetAdvice(
                userA, 10, 2026, BigDecimal.valueOf(50000), BigDecimal.valueOf(15000),
                BigDecimal.valueOf(10000), BigDecimal.valueOf(5000), BigDecimal.valueOf(20000));
        String res2 = aiService.generateBudgetAdvice(
                userA, 10, 2026, BigDecimal.valueOf(50000), BigDecimal.valueOf(15000),
                BigDecimal.valueOf(10000), BigDecimal.valueOf(5000), BigDecimal.valueOf(20000));

        assertThat(res1).isEqualTo("Cached Budget Advice");
        assertThat(res2).isEqualTo("Cached Budget Advice");
        verify(restTemplate, times(1)).postForEntity(anyString(), any(HttpEntity.class), eq(String.class));
    }

    @Test
    @DisplayName("PERF-01 Test 5: User isolation remains strictly preserved")
    void testUserIsolationPreserved() {
        when(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(String.class)))
                .thenReturn(mockGroqResponse("Summary for User A"))
                .thenReturn(mockGroqResponse("Summary for User B"));

        String resA = aiService.generateDailySummary(
                userA, LocalDate.of(2026, 10, 4), BigDecimal.valueOf(500), Map.of(), 0);
        String resB = aiService.generateDailySummary(
                userB, LocalDate.of(2026, 10, 4), BigDecimal.valueOf(500), Map.of(), 0);

        assertThat(resA).isEqualTo("Summary for User A");
        assertThat(resB).isEqualTo("Summary for User B");
        assertThat(resA).isNotEqualTo(resB);
        verify(restTemplate, times(2)).postForEntity(anyString(), any(HttpEntity.class), eq(String.class));
    }

    @Test
    @DisplayName("PERF-01 Test 6: Explicit user eviction removes only targeted user's entries")
    void testExplicitUserEviction() {
        when(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(String.class)))
                .thenReturn(mockGroqResponse("User A response"))
                .thenReturn(mockGroqResponse("User B response"))
                .thenReturn(mockGroqResponse("User A fresh response"));

        aiService.generateBudgetAdvice(userA, 10, 2026, BigDecimal.valueOf(50000), BigDecimal.valueOf(10000),
                BigDecimal.valueOf(10000), BigDecimal.valueOf(5000), BigDecimal.valueOf(25000));
        aiService.generateBudgetAdvice(userB, 10, 2026, BigDecimal.valueOf(50000), BigDecimal.valueOf(10000),
                BigDecimal.valueOf(10000), BigDecimal.valueOf(5000), BigDecimal.valueOf(25000));

        // Evict User A
        aiService.evictUserCache(userA);

        // User B must still hit cache
        String bCached = aiService.generateBudgetAdvice(userB, 10, 2026, BigDecimal.valueOf(50000), BigDecimal.valueOf(10000),
                BigDecimal.valueOf(10000), BigDecimal.valueOf(5000), BigDecimal.valueOf(25000));
        assertThat(bCached).isEqualTo("User B response");
        verify(restTemplate, times(2)).postForEntity(anyString(), any(HttpEntity.class), eq(String.class));

        // User A must regenerate
        String aFresh = aiService.generateBudgetAdvice(userA, 10, 2026, BigDecimal.valueOf(50000), BigDecimal.valueOf(10000),
                BigDecimal.valueOf(10000), BigDecimal.valueOf(5000), BigDecimal.valueOf(25000));
        assertThat(aFresh).isEqualTo("User A fresh response");
        verify(restTemplate, times(3)).postForEntity(anyString(), any(HttpEntity.class), eq(String.class));
    }

    @Test
    @DisplayName("PERF-01 Test 7: Null user ID calls never cache and never leak across callers")
    void testNullUserNeverCaches() {
        when(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(String.class)))
                .thenReturn(mockGroqResponse("Anonymous 1"))
                .thenReturn(mockGroqResponse("Anonymous 2"));

        String first = aiService.generateDailySummary(null, LocalDate.now(), BigDecimal.valueOf(100), Map.of(), 0);
        String second = aiService.generateDailySummary(null, LocalDate.now(), BigDecimal.valueOf(100), Map.of(), 0);

        assertThat(first).isEqualTo("Anonymous 1");
        assertThat(second).isEqualTo("Anonymous 2");
        assertThat(aiService.getSummaryCache()).isEmpty();
        assertThat(aiService.getCacheSize()).isZero();
        verify(restTemplate, times(2)).postForEntity(anyString(), any(HttpEntity.class), eq(String.class));
    }

    @Test
    @DisplayName("PERF-01 Test 8: Concurrent cache reads and writes are thread-safe and bound capacity")
    void testConcurrentCacheAccessThreadSafety() throws InterruptedException {
        int maxCapacity = 5;
        aiService.setMaxCapacity(maxCapacity);

        when(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(String.class)))
                .thenAnswer(inv -> mockGroqResponse("Response " + UUID.randomUUID()));

        int threadCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        List<Throwable> errors = Collections.synchronizedList(new ArrayList<>());
        AtomicInteger successCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            final int index = i;
            executor.submit(() -> {
                try {
                    startLatch.await();
                    UUID u = UUID.randomUUID();
                    aiService.generateDailySummary(
                            u, LocalDate.of(2026, 10, 1 + (index % 5)), BigDecimal.valueOf(100 * (index + 1)), Map.of(), 0);
                    successCount.incrementAndGet();
                } catch (Throwable t) {
                    errors.add(t);
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        boolean completed = doneLatch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(completed).isTrue();
        assertThat(errors).isEmpty();
        assertThat(successCount.get()).isEqualTo(threadCount);

        aiService.cleanUp();
        // Capacity bound must be respected even after heavy concurrent writes
        assertThat(aiService.getCacheSize()).isLessThanOrEqualTo(maxCapacity);
    }
}
