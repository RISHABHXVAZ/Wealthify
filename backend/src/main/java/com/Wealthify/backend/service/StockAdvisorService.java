package com.Wealthify.backend.service;

import com.Wealthify.backend.dto.StockRecommendationResponse;
import com.Wealthify.backend.dto.WastefulAnalysisResponse;
import com.Wealthify.backend.dto.ExpenseItemDto;
import com.Wealthify.backend.entity.Expense;
import com.Wealthify.backend.entity.User;
import com.Wealthify.backend.exception.BusinessException;
import com.Wealthify.backend.repository.ExpenseRepository;
import com.Wealthify.backend.repository.UserRepository;
import com.Wealthify.backend.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class StockAdvisorService {

    private final ExpenseRepository expenseRepository;
    private final UserRepository userRepository;
    private final AiService aiService;

    public WastefulAnalysisResponse getWastefulAnalysis(UserPrincipal principal, int month, int year) {
        LocalDate start = LocalDate.of(year, month, 1);
        LocalDate end = start.withDayOfMonth(start.lengthOfMonth());

        List<Expense> allExpenses = expenseRepository
                .findByUserIdAndExpenseDateBetweenOrderByExpenseDateDesc(principal.getId(), start, end);

        return calculateWastefulAnalysis(principal.getId(), principal.getMonthlyIncome(), month, year, allExpenses);
    }

    public WastefulAnalysisResponse getWastefulAnalysis(String email, int month, int year) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new BusinessException("User not found"));

        LocalDate start = LocalDate.of(year, month, 1);
        LocalDate end = start.withDayOfMonth(start.lengthOfMonth());

        List<Expense> allExpenses = expenseRepository
                .findByUserAndExpenseDateBetweenOrderByExpenseDateDesc(user, start, end);

        return calculateWastefulAnalysis(user.getId(), user.getMonthlyIncome(), month, year, allExpenses);
    }

    private WastefulAnalysisResponse calculateWastefulAnalysis(UUID userId, BigDecimal monthlyIncome,
                                                               int month, int year,
                                                               List<Expense> allExpenses) {
        // Filter wasteful expenses
        List<Expense> wastefulExpenses = allExpenses.stream()
                .filter(e -> Boolean.TRUE.equals(e.getIsFlaggedWasteful()))
                .collect(Collectors.toList());

        // Total amounts
        BigDecimal totalSpent = allExpenses.stream()
                .map(Expense::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalWasteful = wastefulExpenses.stream()
                .map(Expense::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Wasteful percentage
        double wastefulPercentage = totalSpent.compareTo(BigDecimal.ZERO) > 0
                ? totalWasteful.divide(totalSpent, 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100)).doubleValue()
                : 0.0;

        // Wasteful by category
        Map<String, BigDecimal> wastefulByCategory = wastefulExpenses.stream()
                .filter(e -> e.getCategory() != null)
                .collect(Collectors.groupingBy(
                        e -> e.getCategory().getName(),
                        Collectors.reducing(BigDecimal.ZERO,
                                Expense::getAmount, BigDecimal::add)
                ));

        // Convert to DTOs
        List<ExpenseItemDto> wastefulDtos = wastefulExpenses.stream()
                .map(e -> ExpenseItemDto.builder()
                        .id(e.getId())
                        .amount(e.getAmount())
                        .description(e.getDescription())
                        .category(e.getCategory() != null
                                ? e.getCategory().getName() : "Uncategorized")
                        .categoryType(e.getCategory() != null
                                ? e.getCategory().getType() : "WANT")
                        .isFlaggedWasteful(true)
                        .aiConfidence(e.getAiCategoryConfidence() != null
                                ? e.getAiCategoryConfidence() : 0.0)
                        .aiReason(e.getAiReason())
                        .expenseDate(e.getExpenseDate())
                        .build())
                .collect(Collectors.toList());

        // AI recommendations
        BigDecimal income = monthlyIncome != null
                ? monthlyIncome : BigDecimal.ZERO;

        List<String> recommendations = aiService.generateWastefulRecommendations(
                userId, month, year,
                wastefulByCategory, totalWasteful, income
        );

        String aiSummary = wastefulExpenses.isEmpty()
                ? "Great job! No wasteful spending detected this month."
                : aiService.generateDailySummary(userId, LocalDate.of(year, month, 1),
                totalWasteful, wastefulByCategory, wastefulExpenses.size());

        return WastefulAnalysisResponse.builder()
                .totalWastefulAmount(totalWasteful)
                .wastefulPercentage(wastefulPercentage)
                .wastefulTransactionCount(wastefulExpenses.size())
                .wastefulByCategory(wastefulByCategory)
                .wastefulExpenses(wastefulDtos)
                .aiRecommendations(recommendations)
                .aiSummary(aiSummary)
                .build();
    }

    public StockRecommendationResponse getStockRecommendations(
            UserPrincipal principal, int month, int year) {
        if (principal.getMonthlyIncome() == null ||
                principal.getMonthlyIncome().compareTo(BigDecimal.ZERO) == 0) {
            throw new BusinessException(
                    "Please set your monthly income first via Budget Setup.");
        }

        LocalDate start = LocalDate.of(year, month, 1);
        LocalDate end = start.withDayOfMonth(start.lengthOfMonth());

        List<Expense> expenses = expenseRepository
                .findByUserIdAndExpenseDateBetweenOrderByExpenseDateDesc(principal.getId(), start, end);

        return calculateStockRecommendations(
                principal.getId(), principal.getMonthlyIncome(),
                principal.getSavingPercentage(), principal.getInvestmentPercentage(),
                month, year, expenses
        );
    }

    public StockRecommendationResponse getStockRecommendations(
            String email, int month, int year) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new BusinessException("User not found"));

        if (user.getMonthlyIncome() == null ||
                user.getMonthlyIncome().compareTo(BigDecimal.ZERO) == 0) {
            throw new BusinessException(
                    "Please set your monthly income first via Budget Setup.");
        }

        LocalDate start = LocalDate.of(year, month, 1);
        LocalDate end = start.withDayOfMonth(start.lengthOfMonth());

        List<Expense> expenses = expenseRepository
                .findByUserAndExpenseDateBetweenOrderByExpenseDateDesc(user, start, end);

        return calculateStockRecommendations(
                user.getId(), user.getMonthlyIncome(),
                user.getSavingPercentage(), user.getInvestmentPercentage(),
                month, year, expenses
        );
    }

    private StockRecommendationResponse calculateStockRecommendations(
            UUID userId, BigDecimal income, BigDecimal userSavePct, BigDecimal userInvestPct,
            int month, int year, List<Expense> expenses) {

        BigDecimal totalExpenses = expenses.stream()
                .map(Expense::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Use BUDGET SETUP values — not raw calculation
        BigDecimal savePct = userSavePct != null
                ? userSavePct : BigDecimal.valueOf(20);
        BigDecimal investPct = userInvestPct != null
                ? userInvestPct : BigDecimal.valueOf(30);

        // Fixed monthly saving from budget
        BigDecimal monthlySaving = income.multiply(savePct)
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);

        // Fixed investment amount from budget (% of savings)
        BigDecimal investmentAmount = monthlySaving.multiply(investPct)
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);

        BigDecimal availableForExpense = income.subtract(monthlySaving);

        double savingsRate = income.compareTo(BigDecimal.ZERO) > 0
                ? monthlySaving.divide(income, 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100)).doubleValue()
                : 0.0;

        Map<String, BigDecimal> spendingPattern = expenses.stream()
                .filter(e -> e.getCategory() != null)
                .collect(Collectors.groupingBy(
                        e -> e.getCategory().getName(),
                        Collectors.reducing(BigDecimal.ZERO,
                                Expense::getAmount, BigDecimal::add)
                ));

        List<StockRecommendationResponse.StockSuggestion> suggestions =
                aiService.generateStockRecommendations(
                        userId, month, year,
                        investmentAmount, income, spendingPattern);

        String rationale = aiService.generateBudgetAdvice(
                userId, month, year,
                income, totalExpenses, monthlySaving,
                investmentAmount,
                availableForExpense.subtract(totalExpenses));

        return StockRecommendationResponse.builder()
                .monthlyIncome(income)
                .totalMonthlyExpenses(totalExpenses)
                .investableSurplus(investmentAmount)  // ← budget investment amount
                .savingsRate(savingsRate)
                .recommendations(suggestions)
                .aiRationale(rationale)
                .build();
    }
}