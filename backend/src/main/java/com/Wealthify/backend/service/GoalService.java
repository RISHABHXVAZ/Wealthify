package com.Wealthify.backend.service;

import com.Wealthify.backend.dto.*;
import com.Wealthify.backend.entity.*;
import com.Wealthify.backend.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class GoalService {

    private final GoalRepository goalRepository;
    private final UserRepository userRepository;
    private final ExpenseRepository expenseRepository;
    private final AiService aiService;

    // ─── Budget Setup ─────────────────────────────────────────────

    public BudgetAllocationResponse setupBudget(String email,
                                                BudgetSetupRequest request) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));

        user.setMonthlyIncome(request.getMonthlyIncome());
        user.setSavingPercentage(request.getSavingPercentage());
        user.setInvestmentPercentage(request.getInvestmentPercentage());
        userRepository.save(user);

        return buildBudgetResponse(user);
    }

    public BudgetAllocationResponse getBudgetAllocation(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));
        return buildBudgetResponse(user);
    }

    private BudgetAllocationResponse buildBudgetResponse(User user) {
        BigDecimal income = user.getMonthlyIncome() != null
                ? user.getMonthlyIncome() : BigDecimal.ZERO;
        BigDecimal savePct = user.getSavingPercentage() != null
                ? user.getSavingPercentage() : BigDecimal.valueOf(20);
        BigDecimal investPct = user.getInvestmentPercentage() != null
                ? user.getInvestmentPercentage() : BigDecimal.valueOf(30);

        BigDecimal savingAmount = income.multiply(savePct)
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        BigDecimal investmentAmount = savingAmount.multiply(investPct)
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        BigDecimal availableForExpense = income.subtract(savingAmount);

        LocalDate start = LocalDate.of(LocalDate.now().getYear(),
                LocalDate.now().getMonth(), 1);
        List<Expense> thisMonth = expenseRepository
                .findByUserAndExpenseDateBetweenOrderByExpenseDateDesc(
                        user, start, LocalDate.now());

        BigDecimal spentThisMonth = thisMonth.stream()
                .map(Expense::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Wasteful amount this month
        BigDecimal wastefulThisMonth = thisMonth.stream()
                .filter(e -> Boolean.TRUE.equals(e.getIsFlaggedWasteful()))
                .map(Expense::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal remaining = availableForExpense.subtract(spentThisMonth);
        boolean isOverBudget = remaining.compareTo(BigDecimal.ZERO) < 0;

        // Budget used percentage
        double budgetUsedPct = availableForExpense.compareTo(BigDecimal.ZERO) > 0
                ? spentThisMonth.divide(availableForExpense, 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100)).doubleValue()
                : 0.0;

        String aiAdvice = aiService.generateBudgetAdvice(
                income, spentThisMonth, savingAmount,
                investmentAmount, remaining);

        return BudgetAllocationResponse.builder()
                .monthlyIncome(income)
                .savingPercentage(savePct)
                .investmentPercentage(investPct)
                .monthlySavingAmount(savingAmount)
                .monthlyInvestmentAmount(investmentAmount)
                .availableForExpenditure(availableForExpense)
                .spentThisMonth(spentThisMonth)
                .remainingBudget(remaining)
                .isOverBudget(isOverBudget)
                .wastefulThisMonth(wastefulThisMonth)
                .budgetUsedPercentage(budgetUsedPct)
                .aiAdvice(aiAdvice)
                .build();
    }

    // ─── Goals ────────────────────────────────────────────────────

    public GoalResponse createGoal(String email, GoalRequest request) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));

        // Get spending pattern for AI context
        LocalDate threeMonthsAgo = LocalDate.now().minusMonths(3);
        List<Expense> recentExpenses = expenseRepository
                .findByUserAndExpenseDateBetweenOrderByExpenseDateDesc(
                        user, threeMonthsAgo, LocalDate.now());

        // Average monthly expense
        BigDecimal totalRecent = recentExpenses.stream()
                .map(Expense::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal avgMonthly = recentExpenses.isEmpty()
                ? BigDecimal.ZERO
                : totalRecent.divide(BigDecimal.valueOf(3), 2, RoundingMode.HALF_UP);

        // Spending by category
        Map<String, BigDecimal> spendingPattern = recentExpenses.stream()
                .filter(e -> e.getCategory() != null)
                .collect(Collectors.groupingBy(
                        e -> e.getCategory().getName(),
                        Collectors.reducing(BigDecimal.ZERO,
                                Expense::getAmount, BigDecimal::add)
                ));

        BigDecimal income = user.getMonthlyIncome() != null
                ? user.getMonthlyIncome() : BigDecimal.ZERO;
        BigDecimal savePct = user.getSavingPercentage() != null
                ? user.getSavingPercentage() : BigDecimal.valueOf(20);

        // Generate AI plan
        String aiPlan = aiService.generateGoalPlan(
                request.getItemName(), request.getTargetAmount(),
                request.getTargetDate(), income, savePct,
                spendingPattern, avgMonthly);

        // Save goal
        Goal goal = Goal.builder()
                .user(user)
                .itemName(request.getItemName())
                .targetAmount(request.getTargetAmount())
                .targetDate(request.getTargetDate())
                .aiPlan(aiPlan)
                .build();
        goalRepository.save(goal);

        return buildGoalResponse(goal, income, savePct, avgMonthly);
    }

    public List<GoalResponse> getGoals(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));

        BigDecimal income = user.getMonthlyIncome() != null
                ? user.getMonthlyIncome() : BigDecimal.ZERO;
        BigDecimal savePct = user.getSavingPercentage() != null
                ? user.getSavingPercentage() : BigDecimal.valueOf(20);

        return goalRepository.findByUserOrderByCreatedAtDesc(user)
                .stream()
                .map(g -> buildGoalResponse(g, income, savePct, BigDecimal.ZERO))
                .collect(Collectors.toList());
    }

    public GoalResponse updateGoalSaving(String email, UUID goalId,
                                         BigDecimal amount) {
        Goal goal = goalRepository.findById(goalId)
                .orElseThrow(() -> new RuntimeException("Goal not found"));
        if (!goal.getUser().getEmail().equals(email)) {
            throw new RuntimeException("Unauthorized");
        }
        goal.setCurrentSaved(goal.getCurrentSaved().add(amount));
        if (goal.getCurrentSaved().compareTo(goal.getTargetAmount()) >= 0) {
            goal.setStatus("ACHIEVED");
        }
        goalRepository.save(goal);

        User user = goal.getUser();
        BigDecimal income = user.getMonthlyIncome() != null
                ? user.getMonthlyIncome() : BigDecimal.ZERO;
        BigDecimal savePct = user.getSavingPercentage() != null
                ? user.getSavingPercentage() : BigDecimal.valueOf(20);
        return buildGoalResponse(goal, income, savePct, BigDecimal.ZERO);
    }

    public void deleteGoal(String email, UUID goalId) {
        Goal goal = goalRepository.findById(goalId)
                .orElseThrow(() -> new RuntimeException("Goal not found"));
        if (!goal.getUser().getEmail().equals(email)) {
            throw new RuntimeException("Unauthorized");
        }
        goalRepository.delete(goal);
    }

    // ─── Private Helper ───────────────────────────────────────────

    private GoalResponse buildGoalResponse(Goal goal, BigDecimal income,
                                           BigDecimal savePct,
                                           BigDecimal avgMonthly) {
        long monthsRemaining = ChronoUnit.MONTHS.between(
                LocalDate.now(), goal.getTargetDate());
        if (monthsRemaining < 0) monthsRemaining = 0;

        BigDecimal remaining = goal.getTargetAmount()
                .subtract(goal.getCurrentSaved());
        BigDecimal requiredMonthly = monthsRemaining > 0
                ? remaining.divide(BigDecimal.valueOf(monthsRemaining),
                2, RoundingMode.HALF_UP)
                : remaining;

        BigDecimal monthlySaving = income.multiply(savePct)
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);

        boolean isAchievable = requiredMonthly.compareTo(monthlySaving) <= 0;

        String achievabilityReason = isAchievable
                ? "Achievable! You need ₹" + requiredMonthly
                + "/month which is within your saving capacity of ₹"
                + monthlySaving + "/month."
                : "Challenging! You need ₹" + requiredMonthly
                + "/month but your saving capacity is ₹"
                + monthlySaving + "/month. Consider extending the deadline.";

        double progress = goal.getTargetAmount().compareTo(BigDecimal.ZERO) > 0
                ? goal.getCurrentSaved()
                .divide(goal.getTargetAmount(), 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100)).doubleValue()
                : 0.0;

        return GoalResponse.builder()
                .id(goal.getId())
                .itemName(goal.getItemName())
                .targetAmount(goal.getTargetAmount())
                .targetDate(goal.getTargetDate())
                .currentSaved(goal.getCurrentSaved())
                .status(goal.getStatus())
                .aiPlan(goal.getAiPlan())
                .requiredMonthlySaving(requiredMonthly)
                .monthsRemaining((int) monthsRemaining)
                .progressPercentage(progress)
                .isAchievable(isAchievable)
                .achievabilityReason(achievabilityReason)
                .build();
    }
}