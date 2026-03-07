package com.Wealthify.backend.controller;

import com.Wealthify.backend.dto.*;
import com.Wealthify.backend.service.AuthService;
import com.Wealthify.backend.service.StockAdvisorService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;

@RestController
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
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
            @RequestParam(required = false) Integer month,
            @RequestParam(required = false) Integer year) {

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
            @RequestParam(required = false) Integer month,
            @RequestParam(required = false) Integer year) {

        if (month == null) month = LocalDate.now().getMonthValue();
        if (year == null) year = LocalDate.now().getYear();

        return ResponseEntity.ok(
                stockAdvisorService.getStockRecommendations(
                        userDetails.getUsername(), month, year));
    }
}

