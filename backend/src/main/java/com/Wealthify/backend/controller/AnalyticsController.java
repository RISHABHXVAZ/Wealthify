package com.Wealthify.backend.controller;

import com.Wealthify.backend.dto.DailySummaryResponse;
import com.Wealthify.backend.dto.MonthlySummaryResponse;
import com.Wealthify.backend.service.AnalyticsService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;

@RestController
@RequestMapping("/api/analytics")
@RequiredArgsConstructor
@Validated
public class AnalyticsController {

    private final AnalyticsService analyticsService;

    @GetMapping("/daily")
    public ResponseEntity<DailySummaryResponse> getDaily(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {

        if (date == null) date = LocalDate.now();
        return ResponseEntity.ok(
                analyticsService.getDailySummary(userDetails.getUsername(), date));
    }

    @GetMapping("/monthly")
    public ResponseEntity<MonthlySummaryResponse> getMonthly(
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
                analyticsService.getMonthlySummary(userDetails.getUsername(), month, year));
    }
}
