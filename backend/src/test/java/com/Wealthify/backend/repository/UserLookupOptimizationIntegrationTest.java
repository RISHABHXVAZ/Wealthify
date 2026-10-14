package com.Wealthify.backend.repository;

import com.Wealthify.backend.config.AbstractDatabaseIsolationTest;
import com.Wealthify.backend.dto.BudgetAllocationResponse;
import com.Wealthify.backend.dto.ExpenseRequest;
import com.Wealthify.backend.dto.ExpenseResponse;
import com.Wealthify.backend.dto.GoalRequest;
import com.Wealthify.backend.dto.GoalResponse;
import com.Wealthify.backend.dto.LoginRequest;
import com.Wealthify.backend.dto.LoginResponse;
import com.Wealthify.backend.dto.MonthlySummaryResponse;
import com.Wealthify.backend.dto.WastefulAnalysisResponse;
import com.Wealthify.backend.entity.Category;
import com.Wealthify.backend.entity.Expense;
import com.Wealthify.backend.entity.Goal;
import com.Wealthify.backend.entity.User;
import com.Wealthify.backend.exception.BusinessException;
import com.Wealthify.backend.security.UserPrincipal;
import com.Wealthify.backend.service.AnalyticsService;
import com.Wealthify.backend.service.AuthService;
import com.Wealthify.backend.service.ExpenseService;
import com.Wealthify.backend.service.GoalService;
import com.Wealthify.backend.service.StockAdvisorService;
import io.zonky.test.db.AutoConfigureEmbeddedDatabase;
import jakarta.persistence.EntityManager;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureEmbeddedDatabase(provider = AutoConfigureEmbeddedDatabase.DatabaseProvider.ZONKY)
@TestPropertySource(properties = {
        "spring.jpa.properties.hibernate.generate_statistics=true",
        "spring.jpa.show-sql=true"
})
class UserLookupOptimizationIntegrationTest extends AbstractDatabaseIsolationTest {

    @Autowired
    private ExpenseService expenseService;

    @Autowired
    private AnalyticsService analyticsService;

    @Autowired
    private GoalService goalService;

    @Autowired
    private StockAdvisorService stockAdvisorService;

    @Autowired
    private AuthService authService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ExpenseRepository expenseRepository;

    @Autowired
    private GoalRepository goalRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private EntityManager entityManager;

    private SessionFactory sessionFactory;
    private Statistics statistics;

    private User userA;
    private User userB;
    private UserPrincipal principalA;
    private UserPrincipal principalB;

    private final List<UUID> createdUserIds = new ArrayList<>();
    private final List<UUID> createdExpenseIds = new ArrayList<>();
    private final List<UUID> createdGoalIds = new ArrayList<>();

    @BeforeEach
    void setUp() {
        sessionFactory = entityManager.getEntityManagerFactory().unwrap(SessionFactory.class);
        statistics = sessionFactory.getStatistics();
        statistics.setStatisticsEnabled(true);
        statistics.clear();

        userA = User.builder()
                .name("Tenant A")
                .email("tenant.a." + UUID.randomUUID() + "@wealthify.test")
                .password(passwordEncoder.encode("Password123!"))
                .monthlyIncome(BigDecimal.valueOf(100000))
                .savingPercentage(BigDecimal.valueOf(25))
                .investmentPercentage(BigDecimal.valueOf(30))
                .build();
        userA = userRepository.save(userA);
        createdUserIds.add(userA.getId());
        principalA = UserPrincipal.fromEntity(userA);

        userB = User.builder()
                .name("Tenant B")
                .email("tenant.b." + UUID.randomUUID() + "@wealthify.test")
                .password(passwordEncoder.encode("Password123!"))
                .monthlyIncome(BigDecimal.valueOf(60000))
                .savingPercentage(BigDecimal.valueOf(15))
                .investmentPercentage(BigDecimal.valueOf(20))
                .build();
        userB = userRepository.save(userB);
        createdUserIds.add(userB.getId());
        principalB = UserPrincipal.fromEntity(userB);
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
        if (statistics != null) {
            statistics.clear();
        }
    }

    @Test
    @DisplayName("OPT-02 PERF: getExpensesByDate with UserPrincipal executes exactly 1 query (0 queries to users table)")
    void testExpensesByDateWithUserPrincipalExecutesZeroUserQueries() {
        Category foodCategory = categoryRepository.findByNameIgnoreCase("Food").orElseThrow();
        LocalDate testDate = LocalDate.of(2026, 10, 14);

        Expense exp = Expense.builder()
                .user(userA)
                .amount(BigDecimal.valueOf(250))
                .description("Lunch")
                .category(foodCategory)
                .expenseDate(testDate)
                .build();
        exp = expenseRepository.save(exp);
        createdExpenseIds.add(exp.getId());

        // Evict caches to simulate fresh request thread
        sessionFactory.getCache().evictAllRegions();
        entityManager.clear();
        statistics.clear();

        long statementsBefore = statistics.getPrepareStatementCount();
        long userLoadsBefore = statistics.getEntityStatistics("com.Wealthify.backend.entity.User").getLoadCount();

        List<ExpenseResponse> results = expenseService.getExpensesByDate(principalA, testDate);

        long prepareStatements = statistics.getPrepareStatementCount() - statementsBefore;
        long userLoadsAfter = statistics.getEntityStatistics("com.Wealthify.backend.entity.User").getLoadCount() - userLoadsBefore;

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getAmount()).isEqualByComparingTo(BigDecimal.valueOf(250));
        assertThat(userLoadsAfter)
                .as("UserPrincipal overload must not load User entity from the database")
                .isEqualTo(0);
        assertThat(prepareStatements)
                .as("Should execute exactly 1 SQL query for joined expense+category without querying users table")
                .isEqualTo(1);
    }

    @Test
    @DisplayName("OPT-02 PERF: getBudgetAllocation with UserPrincipal executes 0 queries to users table")
    void testBudgetAllocationWithUserPrincipalExecutesZeroUserQueries() {
        sessionFactory.getCache().evictAllRegions();
        entityManager.clear();
        statistics.clear();

        long userLoadsBefore = statistics.getEntityStatistics("com.Wealthify.backend.entity.User").getLoadCount();

        BudgetAllocationResponse budget = goalService.getBudgetAllocation(principalA);

        long userLoadsAfter = statistics.getEntityStatistics("com.Wealthify.backend.entity.User").getLoadCount() - userLoadsBefore;

        assertThat(budget).isNotNull();
        assertThat(budget.getMonthlyIncome()).isEqualByComparingTo(BigDecimal.valueOf(100000));
        assertThat(budget.getMonthlySavingAmount()).isEqualByComparingTo(BigDecimal.valueOf(25000.00));
        assertThat(userLoadsAfter)
                .as("getBudgetAllocation(UserPrincipal) must read financial attributes directly from principal with 0 User loads")
                .isEqualTo(0);
    }

    @Test
    @DisplayName("OPT-02 PERF: getMonthlySummary with UserPrincipal executes 0 queries to users table")
    void testMonthlySummaryWithUserPrincipalExecutesZeroUserQueries() {
        sessionFactory.getCache().evictAllRegions();
        entityManager.clear();
        statistics.clear();

        long userLoadsBefore = statistics.getEntityStatistics("com.Wealthify.backend.entity.User").getLoadCount();

        MonthlySummaryResponse monthly = analyticsService.getMonthlySummary(principalA, 10, 2026);

        long userLoadsAfter = statistics.getEntityStatistics("com.Wealthify.backend.entity.User").getLoadCount() - userLoadsBefore;

        assertThat(monthly).isNotNull();
        assertThat(monthly.getTotalIncome()).isEqualByComparingTo(BigDecimal.valueOf(100000));
        assertThat(userLoadsAfter)
                .as("getMonthlySummary(UserPrincipal) must execute 0 user entity loads")
                .isEqualTo(0);
    }

    @Test
    @DisplayName("OPT-02 PERF: getWastefulAnalysis with UserPrincipal executes 0 queries to users table")
    void testWastefulAnalysisWithUserPrincipalExecutesZeroUserQueries() {
        sessionFactory.getCache().evictAllRegions();
        entityManager.clear();
        statistics.clear();

        long userLoadsBefore = statistics.getEntityStatistics("com.Wealthify.backend.entity.User").getLoadCount();

        WastefulAnalysisResponse wasteful = stockAdvisorService.getWastefulAnalysis(principalA, 10, 2026);

        long userLoadsAfter = statistics.getEntityStatistics("com.Wealthify.backend.entity.User").getLoadCount() - userLoadsBefore;

        assertThat(wasteful).isNotNull();
        assertThat(userLoadsAfter)
                .as("getWastefulAnalysis(UserPrincipal) must execute 0 user entity loads")
                .isEqualTo(0);
    }

    @Test
    @DisplayName("OPT-02 SEC: Strict tenant isolation is enforced across expenses, goals, and deletions")
    void testStrictTenantIsolation() {
        Category foodCategory = categoryRepository.findByNameIgnoreCase("Food").orElseThrow();
        LocalDate testDate = LocalDate.of(2026, 10, 14);

        Expense expA = Expense.builder()
                .user(userA)
                .amount(BigDecimal.valueOf(100))
                .description("User A Lunch")
                .category(foodCategory)
                .expenseDate(testDate)
                .build();
        expA = expenseRepository.save(expA);
        createdExpenseIds.add(expA.getId());

        Expense expB = Expense.builder()
                .user(userB)
                .amount(BigDecimal.valueOf(500))
                .description("User B Dinner")
                .category(foodCategory)
                .expenseDate(testDate)
                .build();
        expB = expenseRepository.save(expB);
        createdExpenseIds.add(expB.getId());

        Goal goalB = Goal.builder()
                .user(userB)
                .itemName("User B Emergency Fund")
                .targetAmount(BigDecimal.valueOf(50000))
                .targetDate(LocalDate.now().plusMonths(6))
                .build();
        goalB = goalRepository.save(goalB);
        createdGoalIds.add(goalB.getId());

        // Verify User A only sees their own expenses
        List<ExpenseResponse> userAExpenses = expenseService.getExpensesByDate(principalA, testDate);
        assertThat(userAExpenses).hasSize(1);
        assertThat(userAExpenses.get(0).getDescription()).isEqualTo("User A Lunch");

        // Verify User A only sees their own goals
        List<GoalResponse> userAGoals = goalService.getGoals(principalA);
        assertThat(userAGoals).isEmpty();

        List<GoalResponse> userBGoals = goalService.getGoals(principalB);
        assertThat(userBGoals).hasSize(1);
        assertThat(userBGoals.get(0).getItemName()).isEqualTo("User B Emergency Fund");

        // Verify User A cannot delete User B's expense
        UUID expenseBId = expB.getId();
        assertThatThrownBy(() -> expenseService.deleteExpense(principalA, expenseBId))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Unauthorized");

        // Verify User A cannot delete User B's goal
        UUID goalBId = goalB.getId();
        assertThatThrownBy(() -> goalService.deleteGoal(principalA, goalBId))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Unauthorized");
    }

    @Test
    @DisplayName("OPT-02: Write/Mutation operations continue to bind and persist managed User entity")
    void testWriteOperationsPersistManagedEntities() {
        Category foodCategory = categoryRepository.findByNameIgnoreCase("Food").orElseThrow();

        ExpenseRequest request = new ExpenseRequest();
        request.setAmount(BigDecimal.valueOf(120));
        request.setDescription("Morning Coffee");
        request.setCategoryId(foodCategory.getId());
        request.setExpenseDate(LocalDate.now());

        ExpenseResponse created = expenseService.addExpense(userA.getEmail(), request);
        createdExpenseIds.add(created.getId());

        Expense saved = expenseRepository.findById(created.getId()).orElseThrow();
        assertThat(saved.getUser().getId()).isEqualTo(userA.getId());
        assertThat(saved.getAmount()).isEqualByComparingTo(BigDecimal.valueOf(120));
    }

    @Test
    @DisplayName("OPT-02 PERF: AuthService login returns UserPrincipal data without redundant findByEmail query")
    void testAuthServiceLoginOptimized() {
        LoginRequest request = new LoginRequest();
        request.setEmail(userA.getEmail());
        request.setPassword("Password123!");

        LoginResponse response = authService.login(request);

        assertThat(response).isNotNull();
        assertThat(response.getToken()).isNotBlank();
        assertThat(response.getEmail()).isEqualTo(userA.getEmail());
        assertThat(response.getName()).isEqualTo("Tenant A");
    }
}
