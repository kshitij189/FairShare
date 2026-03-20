package com.fairshare.repository;

import com.fairshare.entity.ExpenseGroup;
import com.fairshare.entity.OptimisedDebt;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface OptimisedDebtRepository extends JpaRepository<OptimisedDebt, Long> {
    List<OptimisedDebt> findByGroup(ExpenseGroup group);
    void deleteByGroup(ExpenseGroup group);

    @Modifying
    @Query("UPDATE OptimisedDebt d SET d.fromUser = :newUsername WHERE d.fromUser = :oldUsername AND d.group = :group")
    void updateFromUser(@Param("group") ExpenseGroup group, @Param("oldUsername") String oldUsername, @Param("newUsername") String newUsername);

    @Modifying
    @Query("UPDATE OptimisedDebt d SET d.toUser = :newUsername WHERE d.toUser = :oldUsername AND d.group = :group")
    void updateToUser(@Param("group") ExpenseGroup group, @Param("oldUsername") String oldUsername, @Param("newUsername") String newUsername);
}
