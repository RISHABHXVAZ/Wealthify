package com.Wealthify.backend.dto;

import jakarta.validation.constraints.*;
import lombok.Data;
import java.math.BigDecimal;

@Data
public class BudgetSetupRequest {

    @NotNull
    @Positive
    private BigDecimal monthlyIncome;

    // % of income to save (e.g. 20 means 20%)
    @NotNull
    @Min(0) @Max(100)
    private BigDecimal savingPercentage;

    // % of savings to invest (e.g. 30 means 30% of savings)
    @NotNull
    @Min(0) @Max(100)
    private BigDecimal investmentPercentage;
}