package com.Wealthify.backend.dto;

import jakarta.validation.constraints.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class GoalRequest {

    @NotBlank
    @Size(max = 100, message = "Item name must not exceed 100 characters")
    private String itemName;

    @NotNull
    @Positive
    private BigDecimal targetAmount;

    @NotNull
    @Future
    private LocalDate targetDate;
}