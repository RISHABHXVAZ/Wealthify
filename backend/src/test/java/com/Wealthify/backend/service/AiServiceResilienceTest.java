package com.Wealthify.backend.service;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.Wealthify.backend.config.RestClientConfig;
import com.Wealthify.backend.dto.AiCategorizationResult;
import com.Wealthify.backend.dto.StockRecommendationResponse;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class AiServiceResilienceTest {

    private static HttpServer localServer;
    private static String serverBaseUrl;

    private static final String SENSITIVE_API_KEY = "super-secret-groq-key-778899";
    private ObjectMapper objectMapper;

    @BeforeAll
    static void startLocalServer() throws IOException {
        localServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);

        // 1. Slow endpoint: sleeps 400ms before sending response (for read timeout test)
        localServer.createContext("/slow", exchange -> {
            try {
                Thread.sleep(400);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
            byte[] response = "{\"choices\":[{\"message\":{\"content\":\"OK\"}}]}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(response);
            }
        });

        // 2. HTTP 500 endpoint
        localServer.createContext("/error-500", exchange -> {
            byte[] response = "{\"error\": \"Internal Server Error\"}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(500, response.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(response);
            }
        });

        // 3. HTTP 400 endpoint
        localServer.createContext("/error-400", exchange -> {
            byte[] response = "{\"error\": \"Bad Request\"}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(400, response.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(response);
            }
        });

        localServer.setExecutor(null);
        localServer.start();
        serverBaseUrl = "http://127.0.0.1:" + localServer.getAddress().getPort();
    }

    @AfterAll
    static void stopLocalServer() {
        if (localServer != null) {
            localServer.stop(0);
        }
    }

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
    }

    private AiService createAiService(RestTemplate restTemplate, String apiUrl) {
        AiService service = new AiService(restTemplate, objectMapper);
        ReflectionTestUtils.setField(service, "apiKey", SENSITIVE_API_KEY);
        ReflectionTestUtils.setField(service, "apiUrl", apiUrl);
        ReflectionTestUtils.setField(service, "model", "llama-test");
        return service;
    }

    @Test
    @DisplayName("Read timeout on expense categorization triggers fallback without throwing or crashing")
    void testCategorizationReadTimeoutFallback() {
        // Very short read timeout (60ms) against 400ms slow server
        RestTemplate fastTimeoutTemplate = RestClientConfig.createRestTemplate(5000, 60);
        AiService service = createAiService(fastTimeoutTemplate, serverBaseUrl + "/slow");

        long start = System.currentTimeMillis();
        AiCategorizationResult result = service.categorizeExpense(
                "Dinner at 5-star",
                BigDecimal.valueOf(2000),
                BigDecimal.valueOf(15000),
                BigDecimal.valueOf(5000),
                BigDecimal.valueOf(10000)
        );
        long elapsed = System.currentTimeMillis() - start;

        // Bounded: completed well under the 400ms slow server time
        assertThat(elapsed).isLessThan(300);
        assertThat(result).isNotNull();
        assertThat(result.getCategory()).isEqualTo("Miscellaneous");
        assertThat(result.getType()).isEqualTo("WANT");
        assertThat(result.isWasteful()).isFalse();
        assertThat(result.getConfidence()).isEqualTo(0.0);
        assertThat(result.getReason()).contains("Could not categorize automatically");
    }

    @Test
    @DisplayName("Read timeout on AI summaries and tips triggers proper application fallbacks")
    void testSummaryAndTipsReadTimeoutFallback() {
        RestTemplate fastTimeoutTemplate = RestClientConfig.createRestTemplate(5000, 60);
        AiService service = createAiService(fastTimeoutTemplate, serverBaseUrl + "/slow");
        UUID userId = UUID.randomUUID();

        // Daily summary fallback
        String dailySummary = service.generateDailySummary(
                userId, LocalDate.now(), BigDecimal.valueOf(1200), Map.of("Food", BigDecimal.valueOf(1200)), 1
        );
        assertThat(dailySummary).contains("Spent ₹1200 today");

        // Monthly summary fallback
        String monthlySummary = service.generateMonthlySummary(
                userId, 10, 2026, BigDecimal.valueOf(25000), BigDecimal.valueOf(40000), Map.of("Food", BigDecimal.valueOf(25000))
        );
        assertThat(monthlySummary).contains("Total spending this month: ₹25000");

        // Spending tips fallback
        List<String> tips = service.generateSpendingTips(
                userId, 10, 2026, Map.of("Food", BigDecimal.valueOf(25000)), BigDecimal.valueOf(5000), BigDecimal.valueOf(40000)
        );
        assertThat(tips).hasSize(3);
        assertThat(tips.get(0)).contains("Track your daily expenses");

        // Wasteful recommendations fallback
        List<String> wastefulRecs = service.generateWastefulRecommendations(
                userId, 10, 2026, Map.of("Dining Out", BigDecimal.valueOf(4000)), BigDecimal.valueOf(4000), BigDecimal.valueOf(40000)
        );
        assertThat(wastefulRecs).hasSize(4);
        assertThat(wastefulRecs.get(0)).contains("Set a strict budget");

        // Goal plan fallback
        String goalPlan = service.generateGoalPlan(
                userId, "Laptop", BigDecimal.valueOf(50000), LocalDate.now().plusMonths(6),
                BigDecimal.valueOf(20000), BigDecimal.valueOf(20), Map.of(), BigDecimal.valueOf(15000)
        );
        assertThat(goalPlan).contains("create a dedicated savings plan");

        // Budget advice fallback
        String budgetAdvice = service.generateBudgetAdvice(
                userId, 10, 2026, BigDecimal.valueOf(30000), BigDecimal.valueOf(15000),
                BigDecimal.valueOf(5000), BigDecimal.valueOf(5000), BigDecimal.valueOf(5000)
        );
        assertThat(budgetAdvice).contains("Stay on track with your budget");
    }

    @Test
    @DisplayName("HTTP 500 error from external AI service falls back safely without crashing")
    void testHttp500Fallback() {
        RestTemplate template = RestClientConfig.createRestTemplate(5000, 10000);
        AiService service = createAiService(template, serverBaseUrl + "/error-500");

        AiCategorizationResult catResult = service.categorizeExpense("Groceries");
        assertThat(catResult.getCategory()).isEqualTo("Miscellaneous");

        String summary = service.generateDailySummary(BigDecimal.valueOf(500), Map.of(), 0);
        assertThat(summary).contains("Spent ₹500 today");
    }

    @Test
    @DisplayName("HTTP 400 error from external AI service falls back safely without crashing")
    void testHttp400Fallback() {
        RestTemplate template = RestClientConfig.createRestTemplate(5000, 10000);
        AiService service = createAiService(template, serverBaseUrl + "/error-400");

        AiCategorizationResult catResult = service.categorizeExpense("Stationery");
        assertThat(catResult.getCategory()).isEqualTo("Miscellaneous");
    }

    @Test
    @DisplayName("Connection failure (unreachable host/port) falls back immediately without hanging")
    void testConnectionFailureFallback() {
        // Point to a local port that is definitely closed
        RestTemplate template = RestClientConfig.createRestTemplate(2000, 2000);
        AiService service = createAiService(template, "http://127.0.0.1:59998/closed");

        AiCategorizationResult result = service.categorizeExpense("Coffee");
        assertThat(result.getCategory()).isEqualTo("Miscellaneous");

        String dailySummary = service.generateDailySummary(BigDecimal.valueOf(200), Map.of(), 0);
        assertThat(dailySummary).contains("Spent ₹200 today");
    }

    @Test
    @DisplayName("Sanitized logging: sensitive API keys, auth headers, and tokens are never logged on failure")
    void testSanitizedLogging() {
        Logger aiServiceLogger = (Logger) LoggerFactory.getLogger(AiService.class);
        ListAppender<ILoggingEvent> listAppender = new ListAppender<>();
        listAppender.start();
        aiServiceLogger.addAppender(listAppender);

        try {
            RestTemplate template = RestClientConfig.createRestTemplate(2000, 60);
            AiService service = createAiService(template, serverBaseUrl + "/slow");

            service.categorizeExpense("Secret transaction with sensitive notes");

            for (ILoggingEvent event : listAppender.list) {
                String formatted = event.getFormattedMessage();
                assertThat(formatted).doesNotContain(SENSITIVE_API_KEY);
                assertThat(formatted).doesNotContain("Bearer " + SENSITIVE_API_KEY);
                assertThat(formatted).doesNotContain("password");
                assertThat(formatted).doesNotContain("otp");
            }
        } finally {
            aiServiceLogger.detachAppender(listAppender);
        }
    }
}
