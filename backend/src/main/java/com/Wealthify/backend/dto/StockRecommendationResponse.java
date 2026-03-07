package com.Wealthify.backend.dto;

import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
public class StockRecommendationResponse {
    private BigDecimal monthlyIncome;
    private BigDecimal totalMonthlyExpenses;
    private BigDecimal investableSurplus;
    private double savingsRate;
    private List<StockSuggestion> recommendations;
    private String aiRationale;

    @Data
    @Builder
    public static class StockSuggestion {
        private String ticker;
        private String name;
        private String type;           // STOCK / ETF / MUTUAL_FUND
        private String riskLevel;      // LOW / MEDIUM / HIGH
        private String reason;
        private String suggestedAllocation;
    }
}