package com.Wealthify.backend.dto;

import com.Wealthify.backend.entity.Expense;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExpenseResponse {
    private UUID id;
    private BigDecimal amount;
    private String description;
    private CategoryDto category;
    private Double aiCategoryConfidence;

    @JsonProperty("isFlaggedWasteful")
    private Boolean isFlaggedWasteful;

    private String aiReason;
    private LocalDate expenseDate;
    private LocalDateTime createdAt;

    @JsonProperty("flaggedWasteful")
    public Boolean getFlaggedWasteful() {
        return isFlaggedWasteful;
    }

    public static ExpenseResponse fromEntity(Expense expense) {
        if (expense == null) {
            return null;
        }
        return ExpenseResponse.builder()
                .id(expense.getId())
                .amount(expense.getAmount())
                .description(expense.getDescription())
                .category(CategoryDto.fromEntity(expense.getCategory()))
                .aiCategoryConfidence(expense.getAiCategoryConfidence())
                .isFlaggedWasteful(Boolean.TRUE.equals(expense.getIsFlaggedWasteful()))
                .aiReason(expense.getAiReason())
                .expenseDate(expense.getExpenseDate())
                .createdAt(expense.getCreatedAt())
                .build();
    }
}
