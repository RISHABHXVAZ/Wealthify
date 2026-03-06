package com.Wealthify.backend.controller;

import com.Wealthify.backend.dto.ExpenseRequest;
import com.Wealthify.backend.entity.Expense;
import com.Wealthify.backend.service.ExpenseService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/expenses")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class ExpenseController {

    private final ExpenseService expenseService;

    @PostMapping
    public ResponseEntity<Expense> addExpense(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody ExpenseRequest request) {
        return ResponseEntity.ok(
                expenseService.addExpense(userDetails.getUsername(), request));
    }

    @GetMapping("/today")
    public ResponseEntity<List<Expense>> getToday(
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(
                expenseService.getExpensesByDate(
                        userDetails.getUsername(), LocalDate.now()));
    }

    @GetMapping("/date/{date}")
    public ResponseEntity<List<Expense>> getByDate(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable String date) {
        return ResponseEntity.ok(
                expenseService.getExpensesByDate(
                        userDetails.getUsername(), LocalDate.parse(date)));
    }

    @GetMapping("/month/{year}/{month}")
    public ResponseEntity<List<Expense>> getByMonth(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable int year,
            @PathVariable int month) {
        return ResponseEntity.ok(
                expenseService.getExpensesByMonth(
                        userDetails.getUsername(), month, year));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<String> delete(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable UUID id) {
        expenseService.deleteExpense(userDetails.getUsername(), id);
        return ResponseEntity.ok("Deleted successfully");
    }
}