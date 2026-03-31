package com.Wealthify.backend.dto;

import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;

@Data
@Builder
public class BudgetAllocationResponse {
    private BigDecimal monthlyIncome;
    private BigDecimal savingPercentage;
    private BigDecimal investmentPercentage;

    // Calculated amounts
    private BigDecimal monthlySavingAmount;
    private BigDecimal monthlyInvestmentAmount;
    private BigDecimal availableForExpenditure;

    // Current month status
    private BigDecimal spentThisMonth;
    private BigDecimal remainingBudget;
    private boolean isOverBudget;

    // NEW — expose wasteful amount for cross-tab use
    private BigDecimal wastefulThisMonth;
    private double budgetUsedPercentage;

    private String aiAdvice;
}