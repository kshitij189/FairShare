package com.fairshare.repository;

import com.fairshare.entity.ExpenseGroup;
import com.fairshare.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface GroupRepository extends JpaRepository<ExpenseGroup, Long> {
    @Query("SELECT g FROM ExpenseGroup g JOIN g.members m WHERE m = :user ORDER BY g.createdAt DESC")
    List<ExpenseGroup> findByMembersContainingOrderByCreatedAtDesc(@Param("user") User user);

    Optional<ExpenseGroup> findByInviteCode(String inviteCode);
}
