package com.fairshare.repository;

import com.fairshare.entity.Expense;
import com.fairshare.entity.ExpenseComment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ExpenseCommentRepository extends JpaRepository<ExpenseComment, Long> {
    List<ExpenseComment> findByExpenseOrderByCreatedAtAsc(Expense expense);

    @Modifying
    @Query("UPDATE ExpenseComment ec SET ec.author = :newUsername WHERE ec.author = :oldUsername AND ec.expense IN (SELECT e FROM Expense e WHERE e.group.id = :groupId)")
    void updateAuthorByGroup(@Param("groupId") Long groupId, @Param("oldUsername") String oldUsername, @Param("newUsername") String newUsername);
}
