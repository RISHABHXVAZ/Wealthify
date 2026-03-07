package com.Wealthify.backend.dto;

import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Data
@Builder
public class WastefulAnalysisResponse {
    private BigDecimal totalWastefulAmount;
    private double wastefulPercentage;
    private int wastefulTransactionCount;
    private Map<String, BigDecimal> wastefulByCategory;
    private List<ExpenseItemDto> wastefulExpenses;
    private List<String> aiRecommendations;
    private String aiSummary;
}