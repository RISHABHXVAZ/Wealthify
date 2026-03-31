package com.Wealthify.backend.dto;

import jakarta.validation.constraints.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class GoalRequest {

    @NotBlank
    private String itemName;

    @NotNull
    @Positive
    private BigDecimal targetAmount;

    @NotNull
    @Future
    private LocalDate targetDate;
}