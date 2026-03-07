package com.Wealthify.backend.controller;

import com.Wealthify.backend.dto.DailySummaryResponse;
import com.Wealthify.backend.dto.MonthlySummaryResponse;
import com.Wealthify.backend.service.AnalyticsService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;

@RestController
@RequestMapping("/api/analytics")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
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
            @RequestParam(required = false) Integer month,
            @RequestParam(required = false) Integer year) {

        if (month == null) month = LocalDate.now().getMonthValue();
        if (year == null) year = LocalDate.now().getYear();
        return ResponseEntity.ok(
                analyticsService.getMonthlySummary(userDetails.getUsername(), month, year));
    }
}
