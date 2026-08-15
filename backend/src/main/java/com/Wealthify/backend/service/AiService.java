package com.Wealthify.backend.service;

import com.Wealthify.backend.dto.AiCategorizationResult;
import com.Wealthify.backend.dto.StockRecommendationResponse;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
@Slf4j
public class AiService {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${groq.api.key}")
    private String apiKey;

    @Value("${groq.api.url}")
    private String apiUrl;

    @Value("${groq.model}")
    private String model;

    // ─── In-memory cache ────────────────────────────────────────────────────────
    private final Map<String, String> summaryCache = new ConcurrentHashMap<>();
    private final Map<String, Long> cacheTimestamps = new ConcurrentHashMap<>();
    private static final long CACHE_TTL_MS = 10 * 60 * 1000; // 10 minutes

    private String getCached(String key) {
        Long ts = cacheTimestamps.get(key);
        if (ts != null && System.currentTimeMillis() - ts < CACHE_TTL_MS) {
            log.info("Cache hit for key: {}", key);
            return summaryCache.get(key);
        }
        return null;
    }

    private void putCache(String key, String value) {
        summaryCache.put(key, value);
        cacheTimestamps.put(key, System.currentTimeMillis());
        log.info("Cached response for key: {}", key);
    }

    // ─── Retry with exponential backoff ─────────────────────────────────────────
    private String callGroqWithRetry(String prompt) throws Exception {
        int maxRetries = 3;
        Exception lastException = null;

        for (int attempt = 0; attempt < maxRetries; attempt++) {
            try {
                return callGroqForText(prompt);
            } catch (Exception e) {
                lastException = e;
                if (e.getMessage() != null && e.getMessage().contains("429")) {
                    long waitMs = 2000L * (attempt + 1); // 2s, 4s, 6s
                    log.warn("Rate limited (429). Attempt {}/{}. Waiting {}ms before retry...",
                            attempt + 1, maxRetries, waitMs);
                    Thread.sleep(waitMs);
                } else {
                    // Non-rate-limit error — don't retry
                    throw e;
                }
            }
        }
        throw new RuntimeException("Groq API max retries exceeded", lastException);
    }

    public AiCategorizationResult categorizeExpense(String description) {
        return categorizeExpense(description, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
    }

    // ─── Categorization with robust fallback parsing ────────────────────────────
    public AiCategorizationResult categorizeExpense(
            String description,
            BigDecimal amount,
            BigDecimal monthlyIncome,
            BigDecimal monthlySpentSoFar) {
        try {
            String prompt = buildPrompt(description, amount, monthlyIncome, monthlySpentSoFar);

            Map<String, Object> requestBody = Map.of(
                    "model", model,
                    "messages", List.of(
                            Map.of(
                                    "role", "system",
                                    "content", "You are an expense categorizer for Indian college students. Always respond with valid JSON only. No markdown, no extra text."
                            ),
                            Map.of(
                                    "role", "user",
                                    "content", prompt
                            )
                    ),
                    "temperature", 0.1,
                    "reasoning_effort", "low",
                    "max_completion_tokens", 1024
            );

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(apiKey);

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

            ResponseEntity<String> response = restTemplate.postForEntity(
                    apiUrl, entity, String.class
            );

            log.info("Groq response status: {}", response.getStatusCode());
            return parseGroqResponse(response.getBody());

        } catch (Exception e) {
            log.error("AI categorization failed: {}", e.getMessage(), e);
            return getDefaultResult();
        }
    }

    private String buildPrompt(String description, BigDecimal amount,
                               BigDecimal monthlyIncome, BigDecimal monthlySpentSoFar) {

        double spendingRatio = monthlyIncome != null && monthlyIncome.compareTo(BigDecimal.ZERO) > 0
                ? amount.divide(monthlyIncome, 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100)).doubleValue()
                : 0.0;

        double monthlySpentRatio = monthlyIncome != null && monthlyIncome.compareTo(BigDecimal.ZERO) > 0
                ? monthlySpentSoFar.divide(monthlyIncome, 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100)).doubleValue()
                : 0.0;

        return """
            You are a strict expense analyzer for an Indian college student.
            
            Expense: "%s"
            Amount: ₹%s
            Monthly income/allowance: ₹%s
            Amount spent so far this month: ₹%s
            This expense is %.1f%% of monthly income
            Total spent this month so far: %.1f%% of monthly income
            
            STRICT WASTEFUL RULES — follow these exactly:
            
            ALWAYS WASTEFUL (no exceptions):
            - This single expense is more than 20%% of monthly income → WASTEFUL
            - Luxury dining (5-star, 7-star hotel restaurants) → WASTEFUL
            - Alcohol, clubbing, partying → WASTEFUL
            - Impulse gadget purchases → WASTEFUL
            - Premium subscriptions not needed for studies → WASTEFUL
            - Already spent more than 80%% of income this month → flag new WANTs as WASTEFUL
            - Food delivery more than 3 times a week → WASTEFUL
            - Designer clothing, luxury brands → WASTEFUL
            
            NEVER WASTEFUL:
            - Basic food (mess, canteen, normal restaurant once in a while)
            - Transport (auto, bus, reasonable Uber)
            - Haircut, basic grooming
            - Books, courses, education
            - Healthcare, medicines
            - Utilities, rent
            - Reasonable groceries
            
            Available categories: Food, Transport, Housing, Healthcare,
            Utilities, Entertainment, Shopping, Dining Out, Travel,
            Subscriptions, Investments, Savings, Education,
            Personal Care, Miscellaneous
            
            Respond ONLY with this exact JSON schema, no extra text, no markdown code blocks:
            {
              "category": "Dining Out",
              "type": "WANT",
              "isWasteful": true,
              "confidence": 0.95,
              "reason": "One sentence explanation mentioning the percentage of income"
            }
            """.formatted(
                description, amount, monthlyIncome, monthlySpentSoFar,
                spendingRatio, monthlySpentRatio);
    }

    private AiCategorizationResult parseGroqResponse(String responseBody) throws Exception {
        JsonNode root = objectMapper.readTree(responseBody);

        String content = root
                .path("choices")
                .get(0)
                .path("message")
                .path("content")
                .asText();

        content = content.trim();
        // Aggressively clean markdown blocks if the LLM includes them despite instructions
        if (content.contains("```")) {
            content = content.replaceAll("(?s)```json\\s*", "")
                    .replaceAll("(?s)```\\s*", "")
                    .trim();
        }

        log.info("Groq parsed content string: {}", content);

        // Direct mapping into the DTO
        return objectMapper.readValue(content, AiCategorizationResult.class);
    }

    private AiCategorizationResult getDefaultResult() {
        AiCategorizationResult result = new AiCategorizationResult();
        result.setCategory("Miscellaneous");
        result.setType("WANT");
        result.setWasteful(false);
        result.setConfidence(0.0);
        result.setReason("Could not categorize automatically");
        return result;
    }

    public String generateDailySummary(BigDecimal totalSpent,
                                       Map<String, BigDecimal> byCategory,
                                       int wastefulCount) {
        String cacheKey = "daily_summary_" + LocalDate.now() + "_" + totalSpent;
        String cached = getCached(cacheKey);
        if (cached != null) return cached;

        try {
            String prompt = """
                    Give a one sentence friendly summary of today's spending.
                    Total spent: %s INR
                    Categories: %s
                    Wasteful transactions: %d
                    Keep it under 20 words, be direct and helpful.
                    Respond with plain text only, no JSON.
                    """.formatted(totalSpent, byCategory.toString(), wastefulCount);

            String result = callGroqWithRetry(prompt);
            putCache(cacheKey, result);
            return result;
        } catch (Exception e) {
            log.error("Daily summary generation failed: {}", e.getMessage());
            return "Spent ₹" + totalSpent + " today across " + byCategory.size() + " categories.";
        }
    }

    public String generateMonthlySummary(BigDecimal totalSpent,
                                         BigDecimal income,
                                         Map<String, BigDecimal> byCategory) {
        String cacheKey = "monthly_summary_" + LocalDate.now().getYear()
                + "_" + LocalDate.now().getMonthValue() + "_" + totalSpent;
        String cached = getCached(cacheKey);
        if (cached != null) return cached;

        try {
            String prompt = """
                    Give a 2 sentence summary of this month's spending habits.
                    Total spent: %s INR
                    Monthly income: %s INR
                    Top categories: %s
                    Be direct and insightful. Respond with plain text only, no JSON.
                    """.formatted(totalSpent, income, byCategory.toString());

            String result = callGroqWithRetry(prompt);
            putCache(cacheKey, result);
            return result;
        } catch (Exception e) {
            log.error("Monthly summary generation failed: {}", e.getMessage());
            return "Total spending this month: ₹" + totalSpent;
        }
    }

    public List<String> generateSpendingTips(Map<String, BigDecimal> byCategory,
                                             BigDecimal wastefulAmount,
                                             BigDecimal income) {
        String cacheKey = "spending_tips_" + LocalDate.now().getYear()
                + "_" + LocalDate.now().getMonthValue() + "_" + wastefulAmount;
        String cached = getCached(cacheKey);
        if (cached != null) {
            try {
                return objectMapper.readValue(cached,
                        objectMapper.getTypeFactory()
                                .constructCollectionType(List.class, String.class));
            } catch (Exception e) {
                log.warn("Failed to deserialize cached tips, regenerating...");
            }
        }

        try {
            String prompt = """
                    Give exactly 3 specific tips to reduce unnecessary spending.
                    Spending by category: %s
                    Wasteful amount: %s INR
                    Monthly income: %s INR
                    
                    Respond ONLY with a JSON array of 3 strings, no extra text:
                    ["tip 1", "tip 2", "tip 3"]
                    """.formatted(byCategory.toString(), wastefulAmount, income);

            String response = callGroqWithRetry(prompt);
            response = response.trim();
            if (response.contains("```")) {
                response = response.replaceAll("(?s)```json\\s*", "")
                        .replaceAll("(?s)```\\s*", "").trim();
            }

            putCache(cacheKey, response);
            return objectMapper.readValue(response,
                    objectMapper.getTypeFactory()
                            .constructCollectionType(List.class, String.class));
        } catch (Exception e) {
            log.error("Tips generation failed: {}", e.getMessage());
            return List.of(
                    "Track your daily expenses to identify patterns.",
                    "Set a monthly budget for discretionary spending.",
                    "Review subscriptions and cancel unused ones."
            );
        }
    }

    private String callGroqForText(String prompt) throws Exception {
        Map<String, Object> requestBody = Map.of(
                "model", model,
                "messages", List.of(
                        Map.of("role", "user", "content", prompt)
                ),
                "temperature", 0.3,
                "reasoning_effort", "low",
                "max_completion_tokens", 1024
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey);

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);
        ResponseEntity<String> response = restTemplate.postForEntity(apiUrl, entity, String.class);

        JsonNode root = objectMapper.readTree(response.getBody());
        return root.path("choices").get(0)
                .path("message").path("content").asText();
    }

    public List<String> generateWastefulRecommendations(
            Map<String, BigDecimal> wastefulByCategory,
            BigDecimal totalWasteful,
            BigDecimal income) {
        String cacheKey = "wasteful_recs_" + LocalDate.now().getYear()
                + "_" + LocalDate.now().getMonthValue() + "_" + totalWasteful;
        String cached = getCached(cacheKey);
        if (cached != null) {
            try {
                return objectMapper.readValue(cached,
                        objectMapper.getTypeFactory()
                                .constructCollectionType(List.class, String.class));
            } catch (Exception e) {
                log.warn("Failed to deserialize cached wasteful recs, regenerating...");
            }
        }

        try {
            String prompt = """
                    A user has wasteful spending. Give exactly 4 specific actionable tips to reduce it.
                    Wasteful spending by category: %s
                    Total wasteful amount: %s INR
                    Monthly income: %s INR
                    
                    Respond ONLY with a JSON array of 4 strings, no extra text:
                    ["tip 1", "tip 2", "tip 3", "tip 4"]
                    """.formatted(wastefulByCategory.toString(), totalWasteful, income);

            String response = callGroqWithRetry(prompt);
            response = response.trim();
            if (response.contains("```")) {
                response = response.replaceAll("(?s)```json\\s*", "")
                        .replaceAll("(?s)```\\s*", "").trim();
            }

            putCache(cacheKey, response);
            return objectMapper.readValue(response,
                    objectMapper.getTypeFactory()
                            .constructCollectionType(List.class, String.class));
        } catch (Exception e) {
            log.error("Wasteful recommendations failed: {}", e.getMessage());
            return List.of(
                    "Set a strict budget for entertainment and dining out.",
                    "Cook at home instead of ordering food delivery.",
                    "Cancel unused subscriptions.",
                    "Use public transport instead of cabs when possible."
            );
        }
    }

    public List<StockRecommendationResponse.StockSuggestion> generateStockRecommendations(
            BigDecimal surplus,
            BigDecimal income,
            Map<String, BigDecimal> spendingPattern) {
        String cacheKey = "stock_recs_" + LocalDate.now().getYear()
                + "_" + LocalDate.now().getMonthValue() + "_" + surplus;
        String cached = getCached(cacheKey);
        if (cached != null) {
            try {
                return objectMapper.readValue(cached,
                        objectMapper.getTypeFactory().constructCollectionType(
                                List.class, StockRecommendationResponse.StockSuggestion.class));
            } catch (Exception e) {
                log.warn("Failed to deserialize cached stock recs, regenerating...");
            }
        }

        try {
            String prompt = """
                    Give 4 stock/ETF/mutual fund recommendations for an Indian investor.
                    Monthly investable surplus: %s INR
                    Monthly income: %s INR
                    Spending pattern: %s
                    
                    Consider risk appetite based on wasteful spending ratio.
                    Focus on Indian markets (NSE/BSE) and include index funds.
                    
                    Respond ONLY with a JSON array, no extra text:
                    [
                      {
                        "ticker": "NIFTY50",
                        "name": "Nifty 50 Index Fund",
                        "type": "MUTUAL_FUND",
                        "riskLevel": "LOW",
                        "reason": "Stable long-term growth",
                        "suggestedAllocation": "40%%"
                      }
                    ]
                    """.formatted(surplus, income, spendingPattern.toString());

            String response = callGroqWithRetry(prompt);
            response = response.trim();
            if (response.contains("```")) {
                response = response.replaceAll("(?s)```json\\s*", "")
                        .replaceAll("(?s)```\\s*", "").trim();
            }

            putCache(cacheKey, response);
            return objectMapper.readValue(response,
                    objectMapper.getTypeFactory().constructCollectionType(
                            List.class, StockRecommendationResponse.StockSuggestion.class));
        } catch (Exception e) {
            log.error("Stock recommendations failed: {}", e.getMessage());
            return List.of(
                    StockRecommendationResponse.StockSuggestion.builder()
                            .ticker("NIFTYBEES").name("Nippon India ETF Nifty BeES")
                            .type("ETF").riskLevel("LOW")
                            .reason("Best entry point for beginners into Indian equity markets")
                            .suggestedAllocation("40%").build(),
                    StockRecommendationResponse.StockSuggestion.builder()
                            .ticker("PPFAS").name("Parag Parikh Flexi Cap Fund")
                            .type("MUTUAL_FUND").riskLevel("MEDIUM")
                            .reason("Diversified across Indian and global stocks")
                            .suggestedAllocation("30%").build(),
                    StockRecommendationResponse.StockSuggestion.builder()
                            .ticker("GOLDBEES").name("Nippon India ETF Gold BeES")
                            .type("ETF").riskLevel("LOW")
                            .reason("Hedge against inflation and market volatility")
                            .suggestedAllocation("20%").build(),
                    StockRecommendationResponse.StockSuggestion.builder()
                            .ticker("LIQUIDBEES").name("Nippon India ETF Liquid BeES")
                            .type("ETF").riskLevel("LOW")
                            .reason("Liquid emergency fund alternative with better returns than savings")
                            .suggestedAllocation("10%").build()
            );
        }
    }

    public String generateGoalPlan(String itemName, BigDecimal targetAmount,
                                   LocalDate targetDate, BigDecimal monthlyIncome,
                                   BigDecimal savingPercentage,
                                   Map<String, BigDecimal> spendingPattern,
                                   BigDecimal avgMonthlyExpense) {
        String cacheKey = "goal_plan_" + itemName.replaceAll("\\s+", "_")
                + "_" + targetAmount + "_" + targetDate;
        String cached = getCached(cacheKey);
        if (cached != null) return cached;

        try {
            long monthsRemaining = java.time.temporal.ChronoUnit.MONTHS.between(
                    LocalDate.now(), targetDate);
            BigDecimal availableForSaving = monthlyIncome != null
                    ? monthlyIncome.multiply(savingPercentage)
                    .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;
            BigDecimal requiredPerMonth = monthsRemaining > 0
                    ? targetAmount.divide(BigDecimal.valueOf(monthsRemaining),
                    2, RoundingMode.HALF_UP)
                    : targetAmount;

            String prompt = """
                An Indian college student wants to buy: "%s"
                Cost: ₹%s
                Target date: %s (%d months from now)
                Monthly income: ₹%s
                Current saving capacity (%.0f%% of income): ₹%s/month
                Average monthly expenses: ₹%s
                Spending pattern: %s
                Required saving per month for goal: ₹%s
                
                Analyze if this goal is achievable and give a specific actionable plan.
                Consider their spending pattern and suggest exactly where to cut costs.
                
                Respond with a detailed but concise plan in plain text (3-4 sentences max).
                Include: achievability, required monthly saving, specific spending cuts needed.
                """.formatted(
                    itemName, targetAmount, targetDate, monthsRemaining,
                    monthlyIncome, savingPercentage.doubleValue(),
                    availableForSaving, avgMonthlyExpense,
                    spendingPattern.toString(), requiredPerMonth);

            String result = callGroqWithRetry(prompt);
            putCache(cacheKey, result);
            return result;
        } catch (Exception e) {
            log.error("Goal plan generation failed: {}", e.getMessage());
            return "Based on your income and expenses, create a dedicated savings plan for this goal.";
        }
    }

    public String generateBudgetAdvice(BigDecimal income, BigDecimal spent,
                                       BigDecimal savingAmount,
                                       BigDecimal investmentAmount,
                                       BigDecimal available) {
        String cacheKey = "budget_advice_" + LocalDate.now().getYear()
                + "_" + LocalDate.now().getMonthValue() + "_" + spent;
        String cached = getCached(cacheKey);
        if (cached != null) return cached;

        try {
            String prompt = """
                Give a 1-sentence budget health check for this Indian student.
                Monthly income: ₹%s
                Spent this month: ₹%s
                Saving target: ₹%s/month
                Investment target: ₹%s/month
                Remaining budget for expenses: ₹%s
                
                Be direct and encouraging. Plain text only, no JSON.
                """.formatted(income, spent, savingAmount, investmentAmount, available);

            String result = callGroqWithRetry(prompt);
            putCache(cacheKey, result);
            return result;
        } catch (Exception e) {
            log.error("Budget advice failed: {}", e.getMessage());
            return "Stay on track with your budget to meet your saving goals.";
        }
    }
}