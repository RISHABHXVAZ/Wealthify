package com.Wealthify.backend.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;
import java.math.BigDecimal;

@Data
public class IncomeRequest {
    @NotNull
    @Positive
    private BigDecimal income;
}