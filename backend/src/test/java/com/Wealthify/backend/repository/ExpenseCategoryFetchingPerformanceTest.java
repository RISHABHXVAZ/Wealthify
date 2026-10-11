package com.Wealthify.backend.repository;

import com.Wealthify.backend.dto.ExpenseResponse;
import com.Wealthify.backend.entity.Category;
import com.Wealthify.backend.entity.Expense;
import com.Wealthify.backend.entity.User;
import com.Wealthify.backend.service.ExpenseService;
import jakarta.persistence.EntityManager;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import com.Wealthify.backend.config.AbstractDatabaseIsolationTest;
import io.zonky.test.db.AutoConfigureEmbeddedDatabase;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureEmbeddedDatabase(provider = AutoConfigureEmbeddedDatabase.DatabaseProvider.ZONKY)
@TestPropertySource(properties = {
        "spring.jpa.properties.hibernate.generate_statistics=true",
        "spring.jpa.show-sql=true"
})
class ExpenseCategoryFetchingPerformanceTest extends AbstractDatabaseIsolationTest {

    @Autowired
    private ExpenseService expenseService;

    @Autowired
    private ExpenseRepository expenseRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private EntityManager entityManager;

    private User testUser;
    private final List<UUID> createdExpenseIds = new ArrayList<>();
    private final List<UUID> createdUserIds = new ArrayList<>();

    private SessionFactory sessionFactory;
    private Statistics statistics;

    @BeforeEach
    void setUp() {
        sessionFactory = entityManager.getEntityManagerFactory().unwrap(SessionFactory.class);
        statistics = sessionFactory.getStatistics();
        statistics.setStatisticsEnabled(true);
        statistics.clear();

        testUser = User.builder()
                .name("Perf TestUser")
                .email("perf.test." + UUID.randomUUID() + "@example.com")
                .password("encoded_pass_123")
                .monthlyIncome(BigDecimal.valueOf(90000))
                .build();
        testUser = userRepository.save(testUser);
        createdUserIds.add(testUser.getId());
    }

    @AfterEach
    void tearDown() {
        for (UUID expenseId : createdExpenseIds) {
            expenseRepository.findById(expenseId).ifPresent(expenseRepository::delete);
        }
        for (UUID userId : createdUserIds) {
            userRepository.findById(userId).ifPresent(userRepository::delete);
        }
        if (statistics != null) {
            statistics.clear();
        }
    }

    @Test
    @DisplayName("PERF: Retrieving expenses joins Category in single query avoiding N+1 selects across distinct categories")
    void testGetExpensesByDateExecutesSingleJoinedQuery() {
        Category cat1 = categoryRepository.findByNameIgnoreCase("Food").orElseThrow();
        Category cat2 = categoryRepository.findByNameIgnoreCase("Shopping").orElseThrow();
        Category cat3 = categoryRepository.findByNameIgnoreCase("Utilities").orElseThrow();
        Category cat4 = categoryRepository.findByNameIgnoreCase("Entertainment").orElseThrow();

        LocalDate targetDate = LocalDate.of(2026, 10, 6);

        // Create 8 expenses across 4 distinct categories (2 expenses per category)
        Category[] testCategories = {cat1, cat1, cat2, cat2, cat3, cat3, cat4, cat4};
        for (int i = 0; i < testCategories.length; i++) {
            Expense exp = Expense.builder()
                    .user(testUser)
                    .amount(BigDecimal.valueOf(150 + i * 25))
                    .description("Item " + i)
                    .category(testCategories[i])
                    .expenseDate(targetDate)
                    .build();
            exp = expenseRepository.save(exp);
            createdExpenseIds.add(exp.getId());
        }

        // Evict second-level/session cache to simulate fresh request
        sessionFactory.getCache().evictAllRegions();

        // Reset statistics immediately before service invocation
        statistics.clear();
        long statementsBefore = statistics.getPrepareStatementCount();

        List<ExpenseResponse> responses = expenseService.getExpensesByDate(testUser.getEmail(), targetDate);

        long prepareStatements = statistics.getPrepareStatementCount() - statementsBefore;

        // Exactly 2 statements: 1 for userRepository.findByEmail, 1 for joined expense+category retrieval
        // Without @EntityGraph, this would be 1 (user) + 1 (expenses) + 4 (one per distinct category) = 6 statements
        assertThat(responses).hasSize(8);
        assertThat(prepareStatements)
                .as("Should only execute 2 prepared statements (user lookup + joined expenses query) without category N+1")
                .isEqualTo(2);

        // Verify DTO response structure retains all category details properly
        for (ExpenseResponse r : responses) {
            assertThat(r.getCategory()).isNotNull();
            assertThat(r.getCategory().getId()).isNotNull();
            assertThat(r.getCategory().getName()).isNotBlank();
            assertThat(r.getCategory().getType()).isNotBlank();
        }
    }

    @Test
    @DisplayName("PERF: Handles expenses with null/uncategorized category gracefully without errors or extra queries")
    void testGetExpensesWithNullCategoryHandledGracefully() {
        LocalDate targetDate = LocalDate.of(2026, 10, 7);

        Expense exp = Expense.builder()
                .user(testUser)
                .amount(BigDecimal.valueOf(200))
                .description("Uncategorized expense")
                .category(null)
                .expenseDate(targetDate)
                .build();
        exp = expenseRepository.save(exp);
        createdExpenseIds.add(exp.getId());

        sessionFactory.getCache().evictAllRegions();
        statistics.clear();
        long statementsBefore = statistics.getPrepareStatementCount();

        List<ExpenseResponse> responses = expenseService.getExpensesByDate(testUser.getEmail(), targetDate);

        long prepareStatements = statistics.getPrepareStatementCount() - statementsBefore;

        assertThat(responses).hasSize(1);
        assertThat(prepareStatements).isEqualTo(2);
        assertThat(responses.get(0).getCategory()).isNull();
    }
}
