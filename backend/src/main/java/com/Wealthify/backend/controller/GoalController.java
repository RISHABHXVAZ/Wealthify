package com.Wealthify.backend.controller;

import com.Wealthify.backend.dto.*;
import com.Wealthify.backend.service.GoalService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class GoalController {

    private final GoalService goalService;

    // ─── Budget Endpoints ─────────────────────────────────────────

    @PostMapping("/budget/setup")
    public ResponseEntity<BudgetAllocationResponse> setupBudget(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody BudgetSetupRequest request) {
        return ResponseEntity.ok(
                goalService.setupBudget(userDetails.getUsername(), request));
    }

    @GetMapping("/budget/allocation")
    public ResponseEntity<BudgetAllocationResponse> getBudget(
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(
                goalService.getBudgetAllocation(userDetails.getUsername()));
    }

    // ─── Goal Endpoints ───────────────────────────────────────────

    @PostMapping("/goals")
    public ResponseEntity<GoalResponse> createGoal(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody GoalRequest request) {
        return ResponseEntity.ok(
                goalService.createGoal(userDetails.getUsername(), request));
    }

    @GetMapping("/goals")
    public ResponseEntity<List<GoalResponse>> getGoals(
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(
                goalService.getGoals(userDetails.getUsername()));
    }

    @PatchMapping("/goals/{id}/save")
    public ResponseEntity<GoalResponse> updateSaving(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable UUID id,
            @RequestParam BigDecimal amount) {
        return ResponseEntity.ok(
                goalService.updateGoalSaving(
                        userDetails.getUsername(), id, amount));
    }

    @DeleteMapping("/goals/{id}")
    public ResponseEntity<String> deleteGoal(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable UUID id) {
        goalService.deleteGoal(userDetails.getUsername(), id);
        return ResponseEntity.ok("Goal deleted successfully");
    }
}