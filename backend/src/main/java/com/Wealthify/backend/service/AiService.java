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
import java.util.List;
import java.util.Map;

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

    public AiCategorizationResult categorizeExpense(String description, BigDecimal amount) {
        try {
            String prompt = buildPrompt(description, amount);

            // Groq uses OpenAI-compatible format
            Map<String, Object> requestBody = Map.of(
                    "model", model,
                    "messages", List.of(
                            Map.of(
                                    "role", "system",
                                    "content", "You are an expense categorizer. Always respond with valid JSON only. No markdown, no extra text."
                            ),
                            Map.of(
                                    "role", "user",
                                    "content", prompt
                            )
                    ),
                    "temperature", 0.1,
                    "max_tokens", 300
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
            log.error("AI categorization failed: {}", e.getMessage());
            return getDefaultResult();
        }
    }

    private String buildPrompt(String description, BigDecimal amount) {
        return """
            Categorize this expense and respond ONLY with valid JSON, no extra text.
            
            Expense: "%s"
            Amount: %s INR
            
            Available categories: Food, Transport, Housing, Healthcare, Utilities,
            Entertainment, Shopping, Dining Out, Travel, Subscriptions,
            Investments, Savings, Education, Miscellaneous
            
            Rules:
            - type must be one of: NEED, WANT, INVESTMENT
            - isWasteful is true only for unnecessary WANT expenses
            - confidence is between 0.0 and 1.0
            - reason should be one short sentence
            
            Respond with exactly this JSON format:
            {
              "category": "Food",
              "type": "NEED",
              "isWasteful": false,
              "confidence": 0.95,
              "reason": "Basic food expense is a necessity"
            }
            """.formatted(description, amount.toString());
    }

    private AiCategorizationResult parseGroqResponse(String responseBody) throws Exception {
        JsonNode root = objectMapper.readTree(responseBody);

        // Groq/OpenAI response path: choices[0].message.content
        String content = root
                .path("choices")
                .get(0)
                .path("message")
                .path("content")
                .asText();

        content = content.trim();
        if (content.startsWith("```")) {
            content = content.replaceAll("```json\\n?", "")
                    .replaceAll("```\\n?", "")
                    .trim();
        }

        log.info("Groq parsed content: {}", content);
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
        try {
            String prompt = """
            Give a one sentence friendly summary of today's spending.
            Total spent: %s INR
            Categories: %s
            Wasteful transactions: %d
            Keep it under 20 words, be direct and helpful.
            Respond with plain text only, no JSON.
            """.formatted(totalSpent, byCategory.toString(), wastefulCount);

            return callGroqForText(prompt);
        } catch (Exception e) {
            log.error("Daily summary generation failed: {}", e.getMessage());
            return "Spent ₹" + totalSpent + " today across " + byCategory.size() + " categories.";
        }
    }

    public String generateMonthlySummary(BigDecimal totalSpent,
                                         BigDecimal income,
                                         Map<String, BigDecimal> byCategory) {
        try {
            String prompt = """
            Give a 2 sentence summary of this month's spending habits.
            Total spent: %s INR
            Monthly income: %s INR
            Top categories: %s
            Be direct and insightful. Respond with plain text only, no JSON.
            """.formatted(totalSpent, income, byCategory.toString());

            return callGroqForText(prompt);
        } catch (Exception e) {
            log.error("Monthly summary generation failed: {}", e.getMessage());
            return "Total spending this month: ₹" + totalSpent;
        }
    }

    public List<String> generateSpendingTips(Map<String, BigDecimal> byCategory,
                                             BigDecimal wastefulAmount,
                                             BigDecimal income) {
        try {
            String prompt = """
            Give exactly 3 specific tips to reduce unnecessary spending.
            Spending by category: %s
            Wasteful amount: %s INR
            Monthly income: %s INR
            
            Respond ONLY with a JSON array of 3 strings, no extra text:
            ["tip 1", "tip 2", "tip 3"]
            """.formatted(byCategory.toString(), wastefulAmount, income);

            String response = callGroqForText(prompt);
            response = response.trim();
            if (response.startsWith("```")) {
                response = response.replaceAll("```json\\n?", "")
                        .replaceAll("```\\n?", "").trim();
            }
            return objectMapper.readValue(response,
                    objectMapper.getTypeFactory().constructCollectionType(List.class, String.class));
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
                "max_tokens", 300
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
        try {
            String prompt = """
            A user has wasteful spending. Give exactly 4 specific actionable tips to reduce it.
            Wasteful spending by category: %s
            Total wasteful amount: %s INR
            Monthly income: %s INR
            
            Respond ONLY with a JSON array of 4 strings, no extra text:
            ["tip 1", "tip 2", "tip 3", "tip 4"]
            """.formatted(wastefulByCategory.toString(), totalWasteful, income);

            String response = callGroqForText(prompt);
            response = response.trim();
            if (response.startsWith("```")) {
                response = response.replaceAll("```json\\n?", "")
                        .replaceAll("```\\n?", "").trim();
            }
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

            String response = callGroqForText(prompt);
            response = response.trim();
            if (response.startsWith("```")) {
                response = response.replaceAll("```json\\n?", "")
                        .replaceAll("```\\n?", "").trim();
            }
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
}