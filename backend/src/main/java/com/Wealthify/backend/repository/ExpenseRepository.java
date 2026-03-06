package com.Wealthify.backend.repository;

import com.Wealthify.backend.entity.Expense;
import com.Wealthify.backend.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface ExpenseRepository extends JpaRepository<Expense, UUID> {
    List<Expense> findByUserAndExpenseDateBetweenOrderByExpenseDateDesc(
            User user, LocalDate start, LocalDate end
    );
    List<Expense> findByUserOrderByExpenseDateDesc(User user);
}