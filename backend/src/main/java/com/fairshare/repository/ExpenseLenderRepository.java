package com.fairshare.repository;

import com.fairshare.entity.Expense;
import com.fairshare.entity.ExpenseLender;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ExpenseLenderRepository extends JpaRepository<ExpenseLender, Long> {
    List<ExpenseLender> findByExpense(Expense expense);
    void deleteByExpense(Expense expense);

    @Modifying
    @Query("UPDATE ExpenseLender el SET el.username = :newUsername WHERE el.username = :oldUsername AND el.expense IN (SELECT e FROM Expense e WHERE e.group.id = :groupId)")
    void updateUsernameByGroup(@Param("groupId") Long groupId, @Param("oldUsername") String oldUsername, @Param("newUsername") String newUsername);
}
