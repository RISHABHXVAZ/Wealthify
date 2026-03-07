package com.Wealthify.backend.service;

import com.Wealthify.backend.dto.StockRecommendationResponse;
import com.Wealthify.backend.dto.WastefulAnalysisResponse;
import com.Wealthify.backend.dto.ExpenseItemDto;
import com.Wealthify.backend.entity.Expense;
import com.Wealthify.backend.entity.User;
import com.Wealthify.backend.repository.ExpenseRepository;
import com.Wealthify.backend.repository.UserRepository;
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

    public WastefulAnalysisResponse getWastefulAnalysis(String email, int month, int year) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));

        LocalDate start = LocalDate.of(year, month, 1);
        LocalDate end = start.withDayOfMonth(start.lengthOfMonth());

        List<Expense> allExpenses = expenseRepository
                .findByUserAndExpenseDateBetweenOrderByExpenseDateDesc(user, start, end);

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
        BigDecimal income = user.getMonthlyIncome() != null
                ? user.getMonthlyIncome() : BigDecimal.ZERO;

        List<String> recommendations = aiService.generateWastefulRecommendations(
                wastefulByCategory, totalWasteful, income
        );

        String aiSummary = wastefulExpenses.isEmpty()
                ? "Great job! No wasteful spending detected this month."
                : aiService.generateDailySummary(totalWasteful, wastefulByCategory,
                wastefulExpenses.size());

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
            String email, int month, int year) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (user.getMonthlyIncome() == null ||
                user.getMonthlyIncome().compareTo(BigDecimal.ZERO) == 0) {
            throw new RuntimeException(
                    "Please set your monthly income first via POST /api/user/income");
        }

        LocalDate start = LocalDate.of(year, month, 1);
        LocalDate end = start.withDayOfMonth(start.lengthOfMonth());

        List<Expense> expenses = expenseRepository
                .findByUserAndExpenseDateBetweenOrderByExpenseDateDesc(user, start, end);

        BigDecimal totalExpenses = expenses.stream()
                .map(Expense::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal income = user.getMonthlyIncome();
        BigDecimal surplus = income.subtract(totalExpenses);

        // If surplus is negative, set to 0
        if (surplus.compareTo(BigDecimal.ZERO) < 0) {
            surplus = BigDecimal.ZERO;
        }

        double savingsRate = income.compareTo(BigDecimal.ZERO) > 0
                ? surplus.divide(income, 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100)).doubleValue()
                : 0.0;

        // Spending pattern for AI context
        Map<String, BigDecimal> spendingPattern = expenses.stream()
                .filter(e -> e.getCategory() != null)
                .collect(Collectors.groupingBy(
                        e -> e.getCategory().getName(),
                        Collectors.reducing(BigDecimal.ZERO,
                                Expense::getAmount, BigDecimal::add)
                ));

        List<StockRecommendationResponse.StockSuggestion> suggestions =
                aiService.generateStockRecommendations(surplus, income, spendingPattern);

        String rationale = aiService.generateDailySummary(
                surplus, spendingPattern, suggestions.size()
        );

        return StockRecommendationResponse.builder()
                .monthlyIncome(income)
                .totalMonthlyExpenses(totalExpenses)
                .investableSurplus(surplus)
                .savingsRate(savingsRate)
                .recommendations(suggestions)
                .aiRationale(rationale)
                .build();
    }
}