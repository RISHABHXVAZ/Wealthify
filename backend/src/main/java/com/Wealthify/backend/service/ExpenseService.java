package com.Wealthify.backend.service;

import com.Wealthify.backend.dto.AiCategorizationResult;
import com.Wealthify.backend.dto.ExpenseRequest;
import com.Wealthify.backend.entity.*;
import com.Wealthify.backend.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ExpenseService {

    private final ExpenseRepository expenseRepository;
    private final UserRepository userRepository;
    private final CategoryRepository categoryRepository;
    private final AiService aiService;

    public Expense addExpense(String email, ExpenseRequest request) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));

        // Step 1: AI categorization
        log.info("Calling AI to categorize: {}", request.getDescription());
        AiCategorizationResult aiResult = aiService.categorizeExpense(
                request.getDescription(), request.getAmount()
        );
        log.info("AI Result: category={}, wasteful={}, confidence={}",
                aiResult.getCategory(), aiResult.isWasteful(), aiResult.getConfidence());

        // Step 2: Find category from DB (use AI result if no manual categoryId)
        Category category = null;
        if (request.getCategoryId() != null) {
            category = categoryRepository.findById(request.getCategoryId()).orElse(null);
        }

        // If no manual category, find AI suggested category from DB
        if (category == null) {
            category = categoryRepository.findAll()
                    .stream()
                    .filter(c -> c.getName().equalsIgnoreCase(aiResult.getCategory()))
                    .findFirst()
                    .orElse(null);
        }

        // Step 3: Build and save expense with AI metadata
        Expense expense = Expense.builder()
                .user(user)
                .amount(request.getAmount())
                .description(request.getDescription())
                .category(category)
                .aiCategoryConfidence(aiResult.getConfidence())
                .isFlaggedWasteful(aiResult.isWasteful())
                .aiReason(aiResult.getReason())
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