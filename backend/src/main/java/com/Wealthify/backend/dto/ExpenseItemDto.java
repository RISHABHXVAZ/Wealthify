package com.Wealthify.backend.dto;

import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Data
@Builder
public class ExpenseItemDto {
    private UUID id;
    private BigDecimal amount;
    private String description;
    private String category;
    private String categoryType;
    private boolean isFlaggedWasteful;
    private double aiConfidence;
    private String aiReason;
    private LocalDate expenseDate;
}