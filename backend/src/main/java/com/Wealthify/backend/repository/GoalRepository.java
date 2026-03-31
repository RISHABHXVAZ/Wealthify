package com.Wealthify.backend.repository;

import com.Wealthify.backend.entity.Goal;
import com.Wealthify.backend.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface GoalRepository extends JpaRepository<Goal, UUID> {
    List<Goal> findByUserAndStatusOrderByTargetDateAsc(User user, String status);
    List<Goal> findByUserOrderByCreatedAtDesc(User user);
}