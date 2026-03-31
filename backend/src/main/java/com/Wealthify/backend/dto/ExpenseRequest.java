package com.Wealthify.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class ExpenseRequest {
    @NotNull
    @Positive
    private BigDecimal amount;

    @NotBlank
    private String description;

    private Integer categoryId;

    private LocalDate expenseDate;

    // Split expense support — if 4 friends split ₹800, enter 800 and splitCount=4
    // Actual saved amount will be ₹200
    private Integer splitCount;
}