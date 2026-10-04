package com.Wealthify.backend.service;

import com.Wealthify.backend.dto.BudgetSetupRequest;
import com.Wealthify.backend.dto.BudgetAllocationResponse;
import com.Wealthify.backend.entity.User;
import com.Wealthify.backend.repository.ExpenseRepository;
import com.Wealthify.backend.repository.GoalRepository;
import com.Wealthify.backend.repository.UserRepository;
import com.Wealthify.backend.security.JwtUtil;
import com.Wealthify.backend.security.OtpRateLimiter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.client.RestTemplate;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * AI-02 Regression Test Suite:
 * Verifies that updating a user's monthly income invalidates that user's AI cache
 * so subsequent AI requests cannot reuse stale responses generated from the old financial state.
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceIncomeCacheTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtUtil jwtUtil;

    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private JavaMailSender mailSender;

    @Mock
    private OtpRateLimiter otpRateLimiter;

    @Mock
    private GoalRepository goalRepository;

    @Mock
    private ExpenseRepository expenseRepository;

    @Mock
    private RestTemplate restTemplate;

    private ObjectMapper objectMapper;
    private AiService realAiService;
    private AuthService authServiceWithRealCache;
    private AuthService authServiceWithMockAi;
    private AiService mockAiService;

    private User userA;
    private User userB;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        realAiService = new AiService(restTemplate, objectMapper);
        mockAiService = mock(AiService.class);

        authServiceWithRealCache = new AuthService(
                userRepository, passwordEncoder, jwtUtil, authenticationManager, mailSender, otpRateLimiter, realAiService
        );

        authServiceWithMockAi = new AuthService(
                userRepository, passwordEncoder, jwtUtil, authenticationManager, mailSender, otpRateLimiter, mockAiService
        );

        userA = User.builder()
                .id(UUID.randomUUID())
                .name("User A")
                .email("usera@example.com")
                .monthlyIncome(BigDecimal.valueOf(50000))
                .build();

        userB = User.builder()
                .id(UUID.randomUUID())
                .name("User B")
                .email("userb@example.com")
                .monthlyIncome(BigDecimal.valueOf(80000))
                .build();
    }

    @Test
    @DisplayName("AI-02 Test 1 & 5: Income update evicts User A's cache while preserving User B's cache (user isolation)")
    void testIncomeUpdateEvictsOnlyTargetUserCache() {
        // Arrange: Populate real Caffeine AI cache entries for both User A and User B
        String keyA1 = "user_" + userA.getId() + "_daily_summary_2026-10-04_500";
        String keyA2 = "user_" + userA.getId() + "_stock_recs_2026_10_20000";
        String keyB1 = "user_" + userB.getId() + "_daily_summary_2026-10-04_800";
        String keyB2 = "user_" + userB.getId() + "_stock_recs_2026_10_35000";

        realAiService.putCache(keyA1, "User A daily summary based on 50k income");
        realAiService.putCache(keyA2, "User A stock recommendations based on 50k income");
        realAiService.putCache(keyB1, "User B daily summary based on 80k income");
        realAiService.putCache(keyB2, "User B stock recommendations based on 80k income");

        // Verify initial cache state
        assertThat(realAiService.getCached(keyA1)).isEqualTo("User A daily summary based on 50k income");
        assertThat(realAiService.getCached(keyA2)).isEqualTo("User A stock recommendations based on 50k income");
        assertThat(realAiService.getCached(keyB1)).isEqualTo("User B daily summary based on 80k income");
        assertThat(realAiService.getCached(keyB2)).isEqualTo("User B stock recommendations based on 80k income");

        when(userRepository.findByEmail(userA.getEmail())).thenReturn(Optional.of(userA));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act: Update income for User A to 75,000
        BigDecimal newIncome = BigDecimal.valueOf(75000);
        String response = authServiceWithRealCache.updateIncome(userA.getEmail(), newIncome);

        // Assert:
        assertThat(response).contains("75000");

        // User A's cache entries must be evicted
        assertThat(realAiService.getCached(keyA1)).isNull();
        assertThat(realAiService.getCached(keyA2)).isNull();

        // User B's cache entries must remain completely intact
        assertThat(realAiService.getCached(keyB1)).isEqualTo("User B daily summary based on 80k income");
        assertThat(realAiService.getCached(keyB2)).isEqualTo("User B stock recommendations based on 80k income");
    }

    @Test
    @DisplayName("AI-02 Test 2: Successful income update invokes evictUserCache exactly once with target user ID")
    void testSuccessfulIncomeUpdateInvokesEvictUserCacheExactlyOnce() {
        when(userRepository.findByEmail(userA.getEmail())).thenReturn(Optional.of(userA));
        when(userRepository.save(any(User.class))).thenReturn(userA);

        BigDecimal newIncome = BigDecimal.valueOf(60000);
        authServiceWithMockAi.updateIncome(userA.getEmail(), newIncome);

        verify(mockAiService, times(1)).evictUserCache(userA.getId());
        verify(mockAiService, never()).evictUserCache(userB.getId());
    }

    @Test
    @DisplayName("AI-02 Test 3a: User lookup failure throws exception and does NOT evict cache")
    void testUserNotFoundDoesNotEvict() {
        when(userRepository.findByEmail("nonexistent@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authServiceWithMockAi.updateIncome("nonexistent@example.com", BigDecimal.valueOf(60000)))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("User not found");

        verify(mockAiService, never()).evictUserCache(any());
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("AI-02 Test 3b: Invalid non-positive income throws exception and does NOT evict cache")
    void testInvalidIncomeDoesNotEvict() {
        when(userRepository.findByEmail(userA.getEmail())).thenReturn(Optional.of(userA));

        // Test negative income
        assertThatThrownBy(() -> authServiceWithMockAi.updateIncome(userA.getEmail(), BigDecimal.valueOf(-1000)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Monthly income must be greater than zero");

        // Test zero income
        assertThatThrownBy(() -> authServiceWithMockAi.updateIncome(userA.getEmail(), BigDecimal.ZERO))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Monthly income must be greater than zero");

        // Test null income
        assertThatThrownBy(() -> authServiceWithMockAi.updateIncome(userA.getEmail(), null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Monthly income must be greater than zero");

        verify(mockAiService, never()).evictUserCache(any());
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("AI-02 Test 3c: Database persistence failure throws exception and does NOT evict cache (correct order)")
    void testPersistenceFailureDoesNotEvict() {
        when(userRepository.findByEmail(userA.getEmail())).thenReturn(Optional.of(userA));
        when(userRepository.save(any(User.class))).thenThrow(new RuntimeException("Database connection timeout"));

        assertThatThrownBy(() -> authServiceWithMockAi.updateIncome(userA.getEmail(), BigDecimal.valueOf(60000)))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Database connection timeout");

        // Eviction must NEVER happen if persistence failed
        verify(mockAiService, never()).evictUserCache(any());
    }

    @Test
    @DisplayName("AI-02 Test 4: Income actually persists correctly to database entity")
    void testIncomeActuallyPersists() {
        when(userRepository.findByEmail(userA.getEmail())).thenReturn(Optional.of(userA));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        BigDecimal newIncome = BigDecimal.valueOf(95000);
        authServiceWithMockAi.updateIncome(userA.getEmail(), newIncome);

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository, times(1)).save(userCaptor.capture());

        User savedUser = userCaptor.getValue();
        assertThat(savedUser.getMonthlyIncome()).isEqualByComparingTo(newIncome);
        assertThat(userA.getMonthlyIncome()).isEqualByComparingTo(newIncome);
    }

    @Test
    @DisplayName("AI-02 Complementary Test: GoalService budget setup also persists income and evicts AI cache")
    void testGoalServiceBudgetSetupEvictsCache() {
        GoalService goalService = new GoalService(goalRepository, userRepository, expenseRepository, mockAiService);

        when(userRepository.findByEmail(userA.getEmail())).thenReturn(Optional.of(userA));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        BudgetSetupRequest request = new BudgetSetupRequest();
        request.setMonthlyIncome(BigDecimal.valueOf(120000));
        request.setSavingPercentage(BigDecimal.valueOf(25));
        request.setInvestmentPercentage(BigDecimal.valueOf(30));

        BudgetAllocationResponse response = goalService.setupBudget(userA.getEmail(), request);

        assertThat(response).isNotNull();
        assertThat(userA.getMonthlyIncome()).isEqualByComparingTo(BigDecimal.valueOf(120000));
        verify(userRepository, times(1)).save(userA);
        verify(mockAiService, times(1)).evictUserCache(userA.getId());
    }
}
