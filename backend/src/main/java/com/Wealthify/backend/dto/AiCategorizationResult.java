package com.Wealthify.backend.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class AiCategorizationResult {
    private String category;
    private String type;

    @JsonProperty("isWasteful")
    private boolean wasteful;

    private double confidence;
    private String reason;
}