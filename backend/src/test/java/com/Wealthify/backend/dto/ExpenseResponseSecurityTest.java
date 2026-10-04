package com.Wealthify.backend.dto;

import com.Wealthify.backend.entity.Category;
import com.Wealthify.backend.entity.Expense;
import com.Wealthify.backend.entity.User;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ExpenseResponseSecurityTest {

    private ObjectMapper objectMapper;
    private User testUser;
    private Category testCategory;
    private Expense testExpense;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.disable(com.fasterxml.jackson.databind.MapperFeature.REQUIRE_HANDLERS_FOR_JAVA8_TIMES);

        testUser = User.builder()
                .id(UUID.randomUUID())
                .name("Alice Smith")
                .email("alice@example.com")
                .password("$2a$10$abcdefghijklmnopqrstuvwxyz1234567890SECRETBCRYPTHASH")
                .monthlyIncome(BigDecimal.valueOf(100000))
                .resetToken("987654")
                .resetTokenExpiry(LocalDateTime.now().plusMinutes(15))
                .build();

        testCategory = Category.builder()
                .id(1)
                .name("Food & Dining")
                .type("NEED")
                .isEssential(true)
                .build();

        testExpense = Expense.builder()
                .id(UUID.randomUUID())
                .user(testUser)
                .amount(BigDecimal.valueOf(450.50))
                .description("Lunch at Subway")
                .category(testCategory)
                .aiCategoryConfidence(0.95)
                .isFlaggedWasteful(false)
                .aiReason("Regular daily meal within budget")
                .expenseDate(LocalDate.of(2026, 10, 4))
                .createdAt(LocalDateTime.of(2026, 10, 4, 13, 30))
                .build();
    }

    @Test
    @DisplayName("ExpenseResponse contains all required client fields and contract compatibility aliases")
    void testExpenseResponseContainsExpectedFields() throws Exception {
        ExpenseResponse response = ExpenseResponse.fromEntity(testExpense);
        String json = objectMapper.writeValueAsString(response);
        JsonNode root = objectMapper.readTree(json);

        // Core expense fields
        assertThat(root.has("id")).isTrue();
        assertThat(root.get("id").asText()).isEqualTo(testExpense.getId().toString());
        assertThat(root.has("amount")).isTrue();
        assertThat(root.get("amount").asDouble()).isEqualTo(450.50);
        assertThat(root.has("description")).isTrue();
        assertThat(root.get("description").asText()).isEqualTo("Lunch at Subway");
        assertThat(root.has("aiCategoryConfidence")).isTrue();
        assertThat(root.get("aiCategoryConfidence").asDouble()).isEqualTo(0.95);
        assertThat(root.has("aiReason")).isTrue();
        assertThat(root.get("aiReason").asText()).isEqualTo("Regular daily meal within budget");
        assertThat(root.has("expenseDate")).isTrue();
        assertThat(root.get("expenseDate")).isNotNull();

        // Wasteful flags: both isFlaggedWasteful and flaggedWasteful exist for frontend contract compatibility
        assertThat(root.has("isFlaggedWasteful")).isTrue();
        assertThat(root.get("isFlaggedWasteful").asBoolean()).isFalse();
        assertThat(root.has("flaggedWasteful")).isTrue();
        assertThat(root.get("flaggedWasteful").asBoolean()).isFalse();

        // Category fields
        assertThat(root.has("category")).isTrue();
        JsonNode catNode = root.get("category");
        assertThat(catNode.get("id").asInt()).isEqualTo(1);
        assertThat(catNode.get("name").asText()).isEqualTo("Food & Dining");
        assertThat(catNode.get("type").asText()).isEqualTo("NEED");
        assertThat(catNode.get("isEssential").asBoolean()).isTrue();
    }

    @Test
    @DisplayName("ExpenseResponse strictly excludes User and all sensitive security fields")
    void testExpenseResponseExcludesSensitiveUserFields() throws Exception {
        ExpenseResponse response = ExpenseResponse.fromEntity(testExpense);
        String json = objectMapper.writeValueAsString(response);
        JsonNode root = objectMapper.readTree(json);

        // Explicit DTO must not have a 'user' node
        assertThat(root.has("user")).isFalse();

        // Sensitive credentials/tokens must never appear anywhere in the serialized JSON
        assertThat(json).doesNotContain("password");
        assertThat(json).doesNotContain("resetToken");
        assertThat(json).doesNotContain("resetTokenExpiry");
        assertThat(json).doesNotContain("SECRETBCRYPTHASH");
        assertThat(json).doesNotContain("987654");
    }

    @Test
    @DisplayName("List of ExpenseResponse items serializes cleanly without any sensitive leakage")
    void testExpenseResponseListExcludesSensitiveFields() throws Exception {
        Expense expense2 = Expense.builder()
                .id(UUID.randomUUID())
                .user(testUser)
                .amount(BigDecimal.valueOf(1200.00))
                .description("Party drinks")
                .category(testCategory)
                .aiCategoryConfidence(0.99)
                .isFlaggedWasteful(true)
                .aiReason("Discretionary luxury purchase")
                .expenseDate(LocalDate.of(2026, 10, 4))
                .createdAt(LocalDateTime.of(2026, 10, 4, 22, 0))
                .build();

        List<ExpenseResponse> list = List.of(
                ExpenseResponse.fromEntity(testExpense),
                ExpenseResponse.fromEntity(expense2)
        );

        String json = objectMapper.writeValueAsString(list);
        JsonNode root = objectMapper.readTree(json);

        assertThat(root.isArray()).isTrue();
        assertThat(root.size()).isEqualTo(2);

        // Verify both items have expected fields
        for (JsonNode item : root) {
            assertThat(item.has("id")).isTrue();
            assertThat(item.has("amount")).isTrue();
            assertThat(item.has("category")).isTrue();
            assertThat(item.has("user")).isFalse();
        }

        // Entire list JSON must be completely free of sensitive tokens/passwords
        assertThat(json).doesNotContain("password");
        assertThat(json).doesNotContain("resetToken");
        assertThat(json).doesNotContain("resetTokenExpiry");
        assertThat(json).doesNotContain("SECRETBCRYPTHASH");
        assertThat(json).doesNotContain("987654");
    }

    @Test
    @DisplayName("Defense-in-depth: direct Expense entity serialization also prevents User and sensitive field leakage")
    void testDirectExpenseEntitySerializationIgnoresUserAndSensitiveFields() throws Exception {
        // Even if raw Expense entity were serialized, @JsonIgnore on Expense.user and User sensitive fields
        // prevents leakage of password, resetToken, and resetTokenExpiry.
        String expenseJson = objectMapper.writeValueAsString(testExpense);
        JsonNode expenseRoot = objectMapper.readTree(expenseJson);

        assertThat(expenseRoot.has("user")).isFalse();
        assertThat(expenseJson).doesNotContain("password");
        assertThat(expenseJson).doesNotContain("resetToken");
        assertThat(expenseJson).doesNotContain("SECRETBCRYPTHASH");
        assertThat(expenseJson).doesNotContain("987654");

        // Directly serializing User entity also ignores password, resetToken, resetTokenExpiry
        String userJson = objectMapper.writeValueAsString(testUser);
        JsonNode userRoot = objectMapper.readTree(userJson);

        assertThat(userRoot.has("password")).isFalse();
        assertThat(userRoot.has("resetToken")).isFalse();
        assertThat(userRoot.has("resetTokenExpiry")).isFalse();
        assertThat(userJson).doesNotContain("SECRETBCRYPTHASH");
        assertThat(userJson).doesNotContain("987654");
    }
}
