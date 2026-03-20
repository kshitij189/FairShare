package com.fairshare.repository;

import com.fairshare.entity.Expense;
import com.fairshare.entity.ExpenseBorrower;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ExpenseBorrowerRepository extends JpaRepository<ExpenseBorrower, Long> {
    List<ExpenseBorrower> findByExpense(Expense expense);
    void deleteByExpense(Expense expense);

    @Modifying
    @Query("UPDATE ExpenseBorrower eb SET eb.username = :newUsername WHERE eb.username = :oldUsername AND eb.expense IN (SELECT e FROM Expense e WHERE e.group.id = :groupId)")
    void updateUsernameByGroup(@Param("groupId") Long groupId, @Param("oldUsername") String oldUsername, @Param("newUsername") String newUsername);
}
