package com.fairshare.repository;

import com.fairshare.entity.ActivityLog;
import com.fairshare.entity.ExpenseGroup;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ActivityLogRepository extends JpaRepository<ActivityLog, Long> {
    List<ActivityLog> findByGroupOrderByCreatedAtDesc(ExpenseGroup group);

    @Modifying
    @Query("UPDATE ActivityLog al SET al.user = :newUsername WHERE al.user = :oldUsername AND al.group = :group")
    void updateUser(@Param("group") ExpenseGroup group, @Param("oldUsername") String oldUsername, @Param("newUsername") String newUsername);
}
