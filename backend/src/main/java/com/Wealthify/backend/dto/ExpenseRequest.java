package com.Wealthify.backend.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class ExpenseRequest {
    @NotNull
    @Positive
    private BigDecimal amount;

    @NotBlank
    @Size(max = 150, message = "Description must not exceed 150 characters")
    private String description;

    private Integer categoryId;

    private LocalDate expenseDate;

    // Split expense support — if 4 friends split ₹800, enter 800 and splitCount=4
    // Actual saved amount will be ₹200
    @Min(value = 1, message = "Split count must be at least 1")
    @Max(value = 100, message = "Split count cannot exceed 100")
    private Integer splitCount;
}