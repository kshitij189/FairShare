package com.fairshare.repository;

import com.fairshare.entity.Expense;
import com.fairshare.entity.ExpenseGroup;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ExpenseRepository extends JpaRepository<Expense, Long> {
    List<Expense> findByGroupOrderByCreatedAtDesc(ExpenseGroup group);
    Optional<Expense> findByIdAndGroup(Long id, ExpenseGroup group);

    @Query("SELECT e FROM Expense e WHERE e.group = :group ORDER BY e.createdAt DESC")
    List<Expense> findTop100ByGroupOrderByCreatedAtDesc(@Param("group") ExpenseGroup group);

    @Modifying
    @Query("UPDATE Expense e SET e.author = :newUsername WHERE e.author = :oldUsername AND e.group = :group")
    void updateAuthor(@Param("group") ExpenseGroup group, @Param("oldUsername") String oldUsername, @Param("newUsername") String newUsername);

    @Modifying
    @Query("UPDATE Expense e SET e.lender = :newUsername WHERE e.lender = :oldUsername AND e.group = :group")
    void updateLender(@Param("group") ExpenseGroup group, @Param("oldUsername") String oldUsername, @Param("newUsername") String newUsername);
}
