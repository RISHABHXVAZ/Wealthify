package com.Wealthify.backend.dto;

import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Data
@Builder
public class GoalResponse {
    private UUID id;
    private String itemName;
    private BigDecimal targetAmount;
    private LocalDate targetDate;
    private BigDecimal currentSaved;
    private String status;

    // AI Analysis
    private String aiPlan;
    private BigDecimal requiredMonthlySaving;
    private int monthsRemaining;
    private double progressPercentage;
    private boolean isAchievable;
    private String achievabilityReason;
}