package com.Wealthify.backend.repository;

import com.Wealthify.backend.entity.Goal;
import com.Wealthify.backend.entity.User;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface GoalRepository extends JpaRepository<Goal, UUID> {
    List<Goal> findByUserAndStatusOrderByTargetDateAsc(User user, String status);
    List<Goal> findByUserOrderByCreatedAtDesc(User user);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT g FROM Goal g WHERE g.id = :id")
    Optional<Goal> findByIdForUpdate(@Param("id") UUID id);
}