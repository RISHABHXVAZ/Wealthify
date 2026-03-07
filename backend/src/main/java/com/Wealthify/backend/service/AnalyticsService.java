package com.Wealthify.backend.service;

import com.Wealthify.backend.dto.*;
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
public class AnalyticsService {

    private final ExpenseRepository expenseRepository;
    private final UserRepository userRepository;
    private final AiService aiService;

    public DailySummaryResponse getDailySummary(String email, LocalDate date) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));

        List<Expense> expenses = expenseRepository
                .findByUserAndExpenseDateBetweenOrderByExpenseDateDesc(user, date, date);

        return buildDailySummary(date, expenses);
    }

    public MonthlySummaryResponse getMonthlySummary(String email, int month, int year) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));

        LocalDate start = LocalDate.of(year, month, 1);
        LocalDate end = start.withDayOfMonth(start.lengthOfMonth());

        List<Expense> allExpenses = expenseRepository
                .findByUserAndExpenseDateBetweenOrderByExpenseDateDesc(user, start, end);

        // Build daily breakdown
        Map<LocalDate, List<Expense>> byDay = allExpenses.stream()
                .collect(Collectors.groupingBy(Expense::getExpenseDate));

        List<DailySummaryResponse> dailyBreakdown = byDay.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(e -> buildDailySummary(e.getKey(), e.getValue()))
                .collect(Collectors.toList());

        // Total spent
        BigDecimal totalSpent = allExpenses.stream()
                .map(Expense::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Wasteful amount
        BigDecimal wastefulAmount = allExpenses.stream()
                .filter(e -> Boolean.TRUE.equals(e.getIsFlaggedWasteful()))
                .map(Expense::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Spending by category
        Map<String, BigDecimal> spendingByCategory = buildCategoryMap(allExpenses);

        // Income and savings
        BigDecimal income = user.getMonthlyIncome() != null
                ? user.getMonthlyIncome() : BigDecimal.ZERO;
        BigDecimal savings = income.subtract(totalSpent);
        double savingsPercentage = income.compareTo(BigDecimal.ZERO) > 0
                ? savings.divide(income, 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .doubleValue()
                : 0.0;

        // Wasteful percentage
        double wastefulPercentage = totalSpent.compareTo(BigDecimal.ZERO) > 0
                ? wastefulAmount.divide(totalSpent, 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .doubleValue()
                : 0.0;

        // AI monthly summary
        String aiSummary = generateMonthlySummary(
                allExpenses, totalSpent, income, spendingByCategory
        );

        // AI tips
        List<String> aiTips = generateAiTips(
                spendingByCategory, wastefulAmount, income
        );

        return MonthlySummaryResponse.builder()
                .month(month)
                .year(year)
                .totalSpent(totalSpent)
                .totalIncome(income)
                .savingsAmount(savings)
                .savingsPercentage(savingsPercentage)
                .totalTransactions(allExpenses.size())
                .spendingByCategory(spendingByCategory)
                .dailyBreakdown(dailyBreakdown)
                .wastefulAmount(wastefulAmount)
                .wastefulPercentage(wastefulPercentage)
                .aiSummary(aiSummary)
                .aiTips(aiTips)
                .build();
    }

    // ─── Private Helpers ────────────────────────────────────────────

    private DailySummaryResponse buildDailySummary(LocalDate date, List<Expense> expenses) {
        BigDecimal totalSpent = expenses.stream()
                .map(Expense::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal wastefulAmount = expenses.stream()
                .filter(e -> Boolean.TRUE.equals(e.getIsFlaggedWasteful()))
                .map(Expense::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        int wastefulCount = (int) expenses.stream()
                .filter(e -> Boolean.TRUE.equals(e.getIsFlaggedWasteful()))
                .count();

        Map<String, BigDecimal> spendingByCategory = buildCategoryMap(expenses);

        List<ExpenseItemDto> expenseItems = expenses.stream()
                .map(this::toExpenseItemDto)
                .collect(Collectors.toList());

        String aiSummary = expenses.isEmpty()
                ? "No expenses recorded for this day."
                : aiService.generateDailySummary(totalSpent, spendingByCategory, wastefulCount);

        return DailySummaryResponse.builder()
                .date(date)
                .totalSpent(totalSpent)
                .totalTransactions(expenses.size())
                .spendingByCategory(spendingByCategory)
                .expenses(expenseItems)
                .wastefulCount(wastefulCount)
                .wastefulAmount(wastefulAmount)
                .aiSummary(aiSummary)
                .build();
    }

    private Map<String, BigDecimal> buildCategoryMap(List<Expense> expenses) {
        Map<String, BigDecimal> map = new LinkedHashMap<>();
        expenses.stream()
                .filter(e -> e.getCategory() != null)
                .collect(Collectors.groupingBy(
                        e -> e.getCategory().getName(),
                        Collectors.reducing(BigDecimal.ZERO,
                                Expense::getAmount, BigDecimal::add)
                ))
                .entrySet().stream()
                .sorted(Map.Entry.<String, BigDecimal>comparingByValue().reversed())
                .forEach(e -> map.put(e.getKey(), e.getValue()));
        return map;
    }

    private ExpenseItemDto toExpenseItemDto(Expense expense) {
        return ExpenseItemDto.builder()
                .id(expense.getId())
                .amount(expense.getAmount())
                .description(expense.getDescription())
                .category(expense.getCategory() != null
                        ? expense.getCategory().getName() : "Uncategorized")
                .categoryType(expense.getCategory() != null
                        ? expense.getCategory().getType() : "WANT")
                .isFlaggedWasteful(Boolean.TRUE.equals(expense.getIsFlaggedWasteful()))
                .aiConfidence(expense.getAiCategoryConfidence() != null
                        ? expense.getAiCategoryConfidence() : 0.0)
                .aiReason(expense.getAiReason())
                .expenseDate(expense.getExpenseDate())
                .build();
    }

    private String generateMonthlySummary(List<Expense> expenses,
                                          BigDecimal totalSpent,
                                          BigDecimal income,
                                          Map<String, BigDecimal> byCategory) {
        if (expenses.isEmpty()) return "No expenses recorded this month.";
        return aiService.generateMonthlySummary(totalSpent, income, byCategory);
    }

    private List<String> generateAiTips(Map<String, BigDecimal> byCategory,
                                        BigDecimal wastefulAmount,
                                        BigDecimal income) {
        return aiService.generateSpendingTips(byCategory, wastefulAmount, income);
    }
}