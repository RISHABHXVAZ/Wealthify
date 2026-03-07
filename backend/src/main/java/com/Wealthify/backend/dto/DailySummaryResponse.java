package com.Wealthify.backend.dto;

import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Data
@Builder
public class DailySummaryResponse {
    private LocalDate date;
    private BigDecimal totalSpent;
    private int totalTransactions;
    private Map<String, BigDecimal> spendingByCategory;
    private List<ExpenseItemDto> expenses;
    private int wastefulCount;
    private BigDecimal wastefulAmount;
    private String aiSummary;
}