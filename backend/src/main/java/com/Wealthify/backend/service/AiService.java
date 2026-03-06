package com.Wealthify.backend.service;

import com.Wealthify.backend.dto.AiCategorizationResult;
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
}