package com.fairshare.repository;

import com.fairshare.entity.ExpenseGroup;
import com.fairshare.entity.UserDebt;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserDebtRepository extends JpaRepository<UserDebt, Long> {
    List<UserDebt> findByGroup(ExpenseGroup group);
    Optional<UserDebt> findByGroupAndUsername(ExpenseGroup group, String username);
    List<UserDebt> findByGroupAndUsernameIn(ExpenseGroup group, List<String> usernames);
}
