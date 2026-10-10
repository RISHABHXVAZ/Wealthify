package com.Wealthify.backend.controller;

import com.Wealthify.backend.dto.*;
import com.Wealthify.backend.service.AuthService;
import com.Wealthify.backend.service.StockAdvisorService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;

@RestController
@RequiredArgsConstructor
@Validated
public class InsightController {

    private final StockAdvisorService stockAdvisorService;
    private final AuthService authService;

    // Set monthly income
    @PostMapping("/api/user/income")
    public ResponseEntity<String> setIncome(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody IncomeRequest request) {
        return ResponseEntity.ok(
                authService.updateIncome(
                        userDetails.getUsername(), request.getIncome()));
    }

    // Wasteful spending analysis
    @GetMapping("/api/insights/wasteful")
    public ResponseEntity<WastefulAnalysisResponse> getWasteful(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam(required = false)
            @Min(value = 1, message = "Month must be between 1 and 12")
            @Max(value = 12, message = "Month must be between 1 and 12")
            Integer month,
            @RequestParam(required = false)
            @Min(value = 1970, message = "Year must be between 1970 and 2100")
            @Max(value = 2100, message = "Year must be between 1970 and 2100")
            Integer year) {

        if (month != null && (month < 1 || month > 12)) {
            throw new IllegalArgumentException("Month must be between 1 and 12");
        }
        if (year != null && (year < 1970 || year > 2100)) {
            throw new IllegalArgumentException("Year must be between 1970 and 2100");
        }

        if (month == null) month = LocalDate.now().getMonthValue();
        if (year == null) year = LocalDate.now().getYear();

        return ResponseEntity.ok(
                stockAdvisorService.getWastefulAnalysis(
                        userDetails.getUsername(), month, year));
    }

    // Stock recommendations
    @GetMapping("/api/stocks/recommend")
    public ResponseEntity<StockRecommendationResponse> getStockRecommendations(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam(required = false)
            @Min(value = 1, message = "Month must be between 1 and 12")
            @Max(value = 12, message = "Month must be between 1 and 12")
            Integer month,
            @RequestParam(required = false)
            @Min(value = 1970, message = "Year must be between 1970 and 2100")
            @Max(value = 2100, message = "Year must be between 1970 and 2100")
            Integer year) {

        if (month != null && (month < 1 || month > 12)) {
            throw new IllegalArgumentException("Month must be between 1 and 12");
        }
        if (year != null && (year < 1970 || year > 2100)) {
            throw new IllegalArgumentException("Year must be between 1970 and 2100");
        }

        if (month == null) month = LocalDate.now().getMonthValue();
        if (year == null) year = LocalDate.now().getYear();

        return ResponseEntity.ok(
                stockAdvisorService.getStockRecommendations(
                        userDetails.getUsername(), month, year));
    }
}

