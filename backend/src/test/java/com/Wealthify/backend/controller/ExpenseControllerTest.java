package com.Wealthify.backend.controller;

import com.Wealthify.backend.dto.CategoryDto;
import com.Wealthify.backend.dto.ExpenseRequest;
import com.Wealthify.backend.dto.ExpenseResponse;
import com.Wealthify.backend.service.ExpenseService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ExpenseControllerTest {

    @Mock
    private ExpenseService expenseService;

    @InjectMocks
    private ExpenseController expenseController;

    private UserDetails userDetails;
    private ExpenseResponse sampleResponse;

    @BeforeEach
    void setUp() {
        userDetails = new User("user@example.com", "password", Collections.emptyList());

        CategoryDto category = CategoryDto.builder()
                .id(1)
                .name("Food & Dining")
                .type("NEED")
                .isEssential(true)
                .build();

        sampleResponse = ExpenseResponse.builder()
                .id(UUID.randomUUID())
                .amount(BigDecimal.valueOf(250.00))
                .description("Coffee & snack")
                .category(category)
                .aiCategoryConfidence(0.92)
                .isFlaggedWasteful(false)
                .aiReason("Snack within reasonable budget")
                .expenseDate(LocalDate.now())
                .createdAt(LocalDateTime.now())
                .build();
    }

    @Test
    @DisplayName("addExpense returns ResponseEntity<ExpenseResponse>")
    void testAddExpense() {
        ExpenseRequest request = new ExpenseRequest();
        request.setAmount(BigDecimal.valueOf(250.00));
        request.setDescription("Coffee & snack");

        when(expenseService.addExpense(eq("user@example.com"), any(ExpenseRequest.class)))
                .thenReturn(sampleResponse);

        ResponseEntity<ExpenseResponse> response = expenseController.addExpense(userDetails, request);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getId()).isEqualTo(sampleResponse.getId());
        assertThat(response.getBody().getDescription()).isEqualTo("Coffee & snack");
        assertThat(response.getBody().getCategory().getName()).isEqualTo("Food & Dining");
    }

    @Test
    @DisplayName("getToday returns ResponseEntity<List<ExpenseResponse>>")
    void testGetToday() {
        when(expenseService.getExpensesByDate(eq("user@example.com"), any(LocalDate.class)))
                .thenReturn(List.of(sampleResponse));

        ResponseEntity<List<ExpenseResponse>> response = expenseController.getToday(userDetails);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).hasSize(1);
        assertThat(response.getBody().get(0).getDescription()).isEqualTo("Coffee & snack");
    }

    @Test
    @DisplayName("getByDate returns ResponseEntity<List<ExpenseResponse>>")
    void testGetByDate() {
        when(expenseService.getExpensesByDate(eq("user@example.com"), eq(LocalDate.parse("2026-10-04"))))
                .thenReturn(List.of(sampleResponse));

        ResponseEntity<List<ExpenseResponse>> response = expenseController.getByDate(userDetails, "2026-10-04");

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).hasSize(1);
    }

    @Test
    @DisplayName("getByMonth returns ResponseEntity<List<ExpenseResponse>>")
    void testGetByMonth() {
        when(expenseService.getExpensesByMonth(eq("user@example.com"), eq(10), eq(2026)))
                .thenReturn(List.of(sampleResponse));

        ResponseEntity<List<ExpenseResponse>> response = expenseController.getByMonth(userDetails, 2026, 10);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).hasSize(1);
    }

    @Test
    @DisplayName("delete returns 200 OK")
    void testDelete() {
        UUID id = UUID.randomUUID();
        doNothing().when(expenseService).deleteExpense("user@example.com", id);

        ResponseEntity<String> response = expenseController.delete(userDetails, id);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).isEqualTo("Deleted successfully");
        verify(expenseService, times(1)).deleteExpense("user@example.com", id);
    }
}
