package com.fairshare.repository;

import com.fairshare.entity.Debt;
import com.fairshare.entity.ExpenseGroup;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface DebtRepository extends JpaRepository<Debt, Long> {
    List<Debt> findByGroup(ExpenseGroup group);
    Optional<Debt> findByGroupAndFromUserAndToUser(ExpenseGroup group, String fromUser, String toUser);

    @Modifying
    @Query("UPDATE Debt d SET d.fromUser = :newUsername WHERE d.fromUser = :oldUsername AND d.group = :group")
    void updateFromUser(@Param("group") ExpenseGroup group, @Param("oldUsername") String oldUsername, @Param("newUsername") String newUsername);

    @Modifying
    @Query("UPDATE Debt d SET d.toUser = :newUsername WHERE d.toUser = :oldUsername AND d.group = :group")
    void updateToUser(@Param("group") ExpenseGroup group, @Param("oldUsername") String oldUsername, @Param("newUsername") String newUsername);
}
