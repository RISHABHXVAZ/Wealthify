package com.Wealthify.backend.dto;

import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Data
@Builder
public class MonthlySummaryResponse {
    private int month;
    private int year;
    private BigDecimal totalSpent;
    private BigDecimal totalIncome;
    private BigDecimal savingsAmount;
    private double savingsPercentage;
    private int totalTransactions;
    private Map<String, BigDecimal> spendingByCategory;
    private List<DailySummaryResponse> dailyBreakdown;
    private BigDecimal wastefulAmount;
    private double wastefulPercentage;
    private String aiSummary;
    private List<String> aiTips;
}