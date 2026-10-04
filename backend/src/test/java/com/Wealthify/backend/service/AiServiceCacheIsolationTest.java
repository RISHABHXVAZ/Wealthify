package com.Wealthify.backend.service;

import com.Wealthify.backend.dto.StockRecommendationResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AiServiceCacheIsolationTest {

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
    @DisplayName("A. Same user + same request: first computes and caches, second hits cache")
    void testSameUserSameRequestHitsCache() {
        when(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(String.class)))
                .thenReturn(mockGroqResponse("User A's unique summary"));

        // First call - invokes LLM
        String first = aiService.generateDailySummary(
                userA, LocalDate.of(2026, 10, 4), BigDecimal.valueOf(500),
                Map.of("Food", BigDecimal.valueOf(500)), 0);

        // Second call - should hit cache
        String second = aiService.generateDailySummary(
                userA, LocalDate.of(2026, 10, 4), BigDecimal.valueOf(500),
                Map.of("Food", BigDecimal.valueOf(500)), 0);

        assertThat(first).isEqualTo("User A's unique summary");
        assertThat(second).isEqualTo("User A's unique summary");

        // Verify LLM was only called once
        verify(restTemplate, times(1)).postForEntity(anyString(), any(HttpEntity.class), eq(String.class));
    }

    @Test
    @DisplayName("B. Different users + identical request parameters: User B does NOT receive User A's cache")
    void testDifferentUsersIdenticalParametersDoNotLeak() {
        when(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(String.class)))
                .thenReturn(mockGroqResponse("Summary for User A"))
                .thenReturn(mockGroqResponse("Summary for User B"));

        // User A request
        String resultA = aiService.generateDailySummary(
                userA, LocalDate.of(2026, 10, 4), BigDecimal.valueOf(500),
                Map.of("Food", BigDecimal.valueOf(500)), 0);

        // User B request with IDENTICAL request parameters
        String resultB = aiService.generateDailySummary(
                userB, LocalDate.of(2026, 10, 4), BigDecimal.valueOf(500),
                Map.of("Food", BigDecimal.valueOf(500)), 0);

        assertThat(resultA).isEqualTo("Summary for User A");
        assertThat(resultB).isEqualTo("Summary for User B");
        assertThat(resultA).isNotEqualTo(resultB);

        // RestTemplate must have been invoked twice (once per user)
        verify(restTemplate, times(2)).postForEntity(anyString(), any(HttpEntity.class), eq(String.class));

        // Subsequent requests for each user return their own isolated cached result
        String cachedA = aiService.generateDailySummary(
                userA, LocalDate.of(2026, 10, 4), BigDecimal.valueOf(500),
                Map.of("Food", BigDecimal.valueOf(500)), 0);
        String cachedB = aiService.generateDailySummary(
                userB, LocalDate.of(2026, 10, 4), BigDecimal.valueOf(500),
                Map.of("Food", BigDecimal.valueOf(500)), 0);

        assertThat(cachedA).isEqualTo("Summary for User A");
        assertThat(cachedB).isEqualTo("Summary for User B");

        // Still only 2 network calls were made
        verify(restTemplate, times(2)).postForEntity(anyString(), any(HttpEntity.class), eq(String.class));
    }

    @Test
    @DisplayName("C. Different users + different financial data remain independently isolated")
    void testDifferentUsersDifferentData() {
        when(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(String.class)))
                .thenReturn(mockGroqResponse("Great spending habits!"))
                .thenReturn(mockGroqResponse("Warning: high discretionary spending."));

        String resultA = aiService.generateMonthlySummary(
                userA, 10, 2026, BigDecimal.valueOf(1000), BigDecimal.valueOf(50000),
                Map.of("Rent", BigDecimal.valueOf(1000)));

        String resultB = aiService.generateMonthlySummary(
                userB, 10, 2026, BigDecimal.valueOf(25000), BigDecimal.valueOf(30000),
                Map.of("Dining", BigDecimal.valueOf(25000)));

        assertThat(resultA).isEqualTo("Great spending habits!");
        assertThat(resultB).isEqualTo("Warning: high discretionary spending.");
        verify(restTemplate, times(2)).postForEntity(anyString(), any(HttpEntity.class), eq(String.class));
    }

    @Test
    @DisplayName("D. Cache expiry (TTL): expired cache entry is evicted and recomputed")
    void testCacheExpiry() {
        when(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(String.class)))
                .thenReturn(mockGroqResponse("Fresh advice 1"))
                .thenReturn(mockGroqResponse("Fresh advice 2 after TTL"));

        String first = aiService.generateBudgetAdvice(
                userA, 10, 2026, BigDecimal.valueOf(50000), BigDecimal.valueOf(15000),
                BigDecimal.valueOf(10000), BigDecimal.valueOf(5000), BigDecimal.valueOf(20000));
        assertThat(first).isEqualTo("Fresh advice 1");

        // Fast-forward timestamp for this key beyond 10 minute TTL (11 minutes ago)
        String expectedKey = "user_" + userA + "_budget_advice_2026_10_15000";
        aiService.getCacheTimestamps().put(expectedKey, System.currentTimeMillis() - (11 * 60 * 1000));

        // Call again - should detect expiration, evict stale value, and query API again
        String second = aiService.generateBudgetAdvice(
                userA, 10, 2026, BigDecimal.valueOf(50000), BigDecimal.valueOf(15000),
                BigDecimal.valueOf(10000), BigDecimal.valueOf(5000), BigDecimal.valueOf(20000));

        assertThat(second).isEqualTo("Fresh advice 2 after TTL");
        verify(restTemplate, times(2)).postForEntity(anyString(), any(HttpEntity.class), eq(String.class));
    }

    @Test
    @DisplayName("E. User cache eviction: evicting User A preserves User B's cache")
    void testUserCacheEviction() {
        when(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(String.class)))
                .thenReturn(mockGroqResponse("User A advice"))
                .thenReturn(mockGroqResponse("User B advice"))
                .thenReturn(mockGroqResponse("User A new advice after eviction"));

        aiService.generateBudgetAdvice(userA, 10, 2026, BigDecimal.valueOf(50000), BigDecimal.valueOf(10000),
                BigDecimal.valueOf(10000), BigDecimal.valueOf(5000), BigDecimal.valueOf(25000));
        aiService.generateBudgetAdvice(userB, 10, 2026, BigDecimal.valueOf(50000), BigDecimal.valueOf(10000),
                BigDecimal.valueOf(10000), BigDecimal.valueOf(5000), BigDecimal.valueOf(25000));

        verify(restTemplate, times(2)).postForEntity(anyString(), any(HttpEntity.class), eq(String.class));

        // Evict User A's cache only
        aiService.evictUserCache(userA);

        // User B's request should STILL hit cache (no additional postForEntity call)
        String bFromCache = aiService.generateBudgetAdvice(userB, 10, 2026, BigDecimal.valueOf(50000), BigDecimal.valueOf(10000),
                BigDecimal.valueOf(10000), BigDecimal.valueOf(5000), BigDecimal.valueOf(25000));
        assertThat(bFromCache).isEqualTo("User B advice");
        verify(restTemplate, times(2)).postForEntity(anyString(), any(HttpEntity.class), eq(String.class));

        // User A's request must call postForEntity again
        String aFresh = aiService.generateBudgetAdvice(userA, 10, 2026, BigDecimal.valueOf(50000), BigDecimal.valueOf(10000),
                BigDecimal.valueOf(10000), BigDecimal.valueOf(5000), BigDecimal.valueOf(25000));
        assertThat(aFresh).isEqualTo("User A new advice after eviction");
        verify(restTemplate, times(3)).postForEntity(anyString(), any(HttpEntity.class), eq(String.class));
    }

    @Test
    @DisplayName("F. Null User ID safety: unauthenticated/anonymous calls never cache")
    void testNullUserIdNeverCaches() {
        when(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(String.class)))
                .thenReturn(mockGroqResponse("No-cache call 1"))
                .thenReturn(mockGroqResponse("No-cache call 2"));

        // Invocations with null userId
        String first = aiService.generateDailySummary(null, LocalDate.now(), BigDecimal.valueOf(100), Map.of(), 0);
        String second = aiService.generateDailySummary(null, LocalDate.now(), BigDecimal.valueOf(100), Map.of(), 0);

        assertThat(first).isEqualTo("No-cache call 1");
        assertThat(second).isEqualTo("No-cache call 2");

        // Verify cache map contains zero entries
        assertThat(aiService.getSummaryCache()).isEmpty();
        verify(restTemplate, times(2)).postForEntity(anyString(), any(HttpEntity.class), eq(String.class));
    }

    @Test
    @DisplayName("G. All AI cached features enforce user-scoped cache keys")
    void testAllFeaturesEnforceUserIsolation() {
        when(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(String.class)))
                .thenReturn(mockGroqResponse("Daily Summary"))
                .thenReturn(mockGroqResponse("Monthly Summary"))
                .thenReturn(mockGroqResponse("[\"Tip 1\", \"Tip 2\", \"Tip 3\"]"))
                .thenReturn(mockGroqResponse("[\"Rec 1\", \"Rec 2\", \"Rec 3\", \"Rec 4\"]"))
                .thenReturn(mockGroqResponse("[{\"ticker\":\"NIFTY50\",\"name\":\"Nifty 50\",\"type\":\"ETF\",\"riskLevel\":\"LOW\",\"reason\":\"Good\",\"suggestedAllocation\":\"50%\"}]"))
                .thenReturn(mockGroqResponse("Goal Plan"))
                .thenReturn(mockGroqResponse("Budget Advice"));

        aiService.generateDailySummary(userA, LocalDate.of(2026, 10, 4), BigDecimal.valueOf(100), Map.of(), 0);
        aiService.generateMonthlySummary(userA, 10, 2026, BigDecimal.valueOf(500), BigDecimal.valueOf(5000), Map.of());
        aiService.generateSpendingTips(userA, 10, 2026, Map.of(), BigDecimal.valueOf(50), BigDecimal.valueOf(5000));
        aiService.generateWastefulRecommendations(userA, 10, 2026, Map.of(), BigDecimal.valueOf(50), BigDecimal.valueOf(5000));
        aiService.generateStockRecommendations(userA, 10, 2026, BigDecimal.valueOf(1000), BigDecimal.valueOf(5000), Map.of());
        aiService.generateGoalPlan(userA, "Laptop", BigDecimal.valueOf(50000), LocalDate.of(2027, 1, 1), BigDecimal.valueOf(5000), BigDecimal.valueOf(20), Map.of(), BigDecimal.valueOf(2000));
        aiService.generateBudgetAdvice(userA, 10, 2026, BigDecimal.valueOf(5000), BigDecimal.valueOf(1000), BigDecimal.valueOf(1000), BigDecimal.valueOf(500), BigDecimal.valueOf(2500));

        // Verify every single key in the cache starts with "user_" + userA + "_"
        assertThat(aiService.getSummaryCache()).isNotEmpty();
        for (String key : aiService.getSummaryCache().keySet()) {
            assertThat(key).startsWith("user_" + userA + "_");
        }
    }
}
