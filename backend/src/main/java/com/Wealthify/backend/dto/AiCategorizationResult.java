package com.Wealthify.backend.dto;

import lombok.Data;

@Data
public class AiCategorizationResult {
    private String category;
    private String type;           // NEED / WANT / INVESTMENT
    private boolean isWasteful;
    private double confidence;
    private String reason;
}