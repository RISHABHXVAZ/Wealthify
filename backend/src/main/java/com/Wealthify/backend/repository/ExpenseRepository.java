package com.Wealthify.backend.repository;

import com.Wealthify.backend.entity.Expense;
import com.Wealthify.backend.entity.User;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface ExpenseRepository extends JpaRepository<Expense, UUID> {
    @EntityGraph(attributePaths = {"category"})
    List<Expense> findByUserAndExpenseDateBetweenOrderByExpenseDateDesc(
            User user, LocalDate start, LocalDate end
    );

    @EntityGraph(attributePaths = {"category"})
    List<Expense> findByUserOrderByExpenseDateDesc(User user);

    @EntityGraph(attributePaths = {"category"})
    List<Expense> findByUserIdAndExpenseDateBetweenOrderByExpenseDateDesc(
            UUID userId, LocalDate start, LocalDate end
    );

    @EntityGraph(attributePaths = {"category"})
    List<Expense> findByUserIdOrderByExpenseDateDesc(UUID userId);
}
