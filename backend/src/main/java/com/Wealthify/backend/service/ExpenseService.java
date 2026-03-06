package com.Wealthify.backend.service;

import com.Wealthify.backend.dto.ExpenseRequest;
import com.Wealthify.backend.entity.*;
import com.Wealthify.backend.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ExpenseService {

    private final ExpenseRepository expenseRepository;
    private final UserRepository userRepository;
    private final CategoryRepository categoryRepository;

    public Expense addExpense(String email, ExpenseRequest request) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));

        Category category = null;
        if (request.getCategoryId() != null) {
            category = categoryRepository.findById(request.getCategoryId()).orElse(null);
        }

        Expense expense = Expense.builder()
                .user(user)
                .amount(request.getAmount())
                .description(request.getDescription())
                .category(category)
                .expenseDate(request.getExpenseDate() != null
                        ? request.getExpenseDate() : LocalDate.now())
                .build();

        return expenseRepository.save(expense);
    }

    public List<Expense> getExpensesByDate(String email, LocalDate date) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));
        return expenseRepository
                .findByUserAndExpenseDateBetweenOrderByExpenseDateDesc(user, date, date);
    }

    public List<Expense> getExpensesByMonth(String email, int month, int year) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));
        LocalDate start = LocalDate.of(year, month, 1);
        LocalDate end = start.withDayOfMonth(start.lengthOfMonth());
        return expenseRepository
                .findByUserAndExpenseDateBetweenOrderByExpenseDateDesc(user, start, end);
    }

    public void deleteExpense(String email, UUID expenseId) {
        Expense expense = expenseRepository.findById(expenseId)
                .orElseThrow(() -> new RuntimeException("Expense not found"));
        if (!expense.getUser().getEmail().equals(email)) {
            throw new RuntimeException("Unauthorized");
        }
        expenseRepository.delete(expense);
    }
}