package com.fairshare.controller;

import com.fairshare.dto.GroupDto;
import com.fairshare.dto.InviteInfoDto;
import com.fairshare.entity.*;
import com.fairshare.repository.*;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/invite")
public class InviteController {

    private final GroupRepository groupRepository;
    private final UserRepository userRepository;
    private final UserDebtRepository userDebtRepository;
    private final DebtRepository debtRepository;
    private final OptimisedDebtRepository optimisedDebtRepository;
    private final ExpenseRepository expenseRepository;
    private final ExpenseLenderRepository expenseLenderRepository;
    private final ExpenseBorrowerRepository expenseBorrowerRepository;
    private final ExpenseCommentRepository expenseCommentRepository;
    private final ActivityLogRepository activityLogRepository;

    public InviteController(GroupRepository groupRepository, UserRepository userRepository,
                           UserDebtRepository userDebtRepository, DebtRepository debtRepository,
                           OptimisedDebtRepository optimisedDebtRepository, ExpenseRepository expenseRepository,
                           ExpenseLenderRepository expenseLenderRepository, ExpenseBorrowerRepository expenseBorrowerRepository,
                           ExpenseCommentRepository expenseCommentRepository, ActivityLogRepository activityLogRepository) {
        this.groupRepository = groupRepository;
        this.userRepository = userRepository;
        this.userDebtRepository = userDebtRepository;
        this.debtRepository = debtRepository;
        this.optimisedDebtRepository = optimisedDebtRepository;
        this.expenseRepository = expenseRepository;
        this.expenseLenderRepository = expenseLenderRepository;
        this.expenseBorrowerRepository = expenseBorrowerRepository;
        this.expenseCommentRepository = expenseCommentRepository;
        this.activityLogRepository = activityLogRepository;
    }

    @GetMapping("/{inviteCode}")
    public ResponseEntity<?> getInviteInfo(@PathVariable String inviteCode) {
        Optional<ExpenseGroup> groupOpt = groupRepository.findByInviteCode(inviteCode);
        if (groupOpt.isEmpty()) {
            return ResponseEntity.status(404).body(Map.of("error", "Invalid invite link"));
        }

        ExpenseGroup group = groupOpt.get();
        List<InviteInfoDto.InviteMemberDto> members = group.getMembers().stream()
                .map(m -> InviteInfoDto.InviteMemberDto.builder()
                        .id(m.getId())
                        .username(m.getUsername())
                        .isDummy(!m.isHasUsablePassword())
                        .build())
                .collect(Collectors.toList());

        InviteInfoDto dto = InviteInfoDto.builder()
                .groupId(group.getId())
                .groupName(group.getName())
                .inviteCode(group.getInviteCode())
                .members(members)
                .build();

        return ResponseEntity.ok(dto);
    }

    @PostMapping("/{inviteCode}/claim")
    @Transactional
    public ResponseEntity<?> claimInvite(@AuthenticationPrincipal User currentUser,
                                          @PathVariable String inviteCode,
                                          @RequestBody Map<String, Object> body) {
        if (currentUser == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Authentication required"));
        }

        Object memberIdObj = body.get("member_id");
        if (memberIdObj == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "member_id is required"));
        }
        Long memberId = Long.valueOf(memberIdObj.toString());

        Optional<ExpenseGroup> groupOpt = groupRepository.findByInviteCode(inviteCode);
        if (groupOpt.isEmpty()) {
            return ResponseEntity.status(404).body(Map.of("error", "Invalid invite link"));
        }

        ExpenseGroup group = groupOpt.get();

        // Check if current user is already a member
        boolean alreadyMember = group.getMembers().stream()
                .anyMatch(m -> m.getId().equals(currentUser.getId()));
        if (alreadyMember) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "You are already a member of this group"));
        }

        // Find the dummy member
        Optional<User> dummyOpt = userRepository.findById(memberId);
        if (dummyOpt.isEmpty()) {
            return ResponseEntity.status(404).body(Map.of("error", "Member not found"));
        }

        User dummy = dummyOpt.get();
        boolean inGroup = group.getMembers().stream()
                .anyMatch(m -> m.getId().equals(dummy.getId()));
        if (!inGroup) {
            return ResponseEntity.status(404).body(Map.of("error", "Member not found"));
        }

        if (dummy.isHasUsablePassword()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "This member already has an account. They need to join using their own credentials."));
        }

        String oldUsername = dummy.getUsername();
        String newUsername = currentUser.getUsername();

        // Remove dummy from group, add current user
        group.getMembers().removeIf(m -> m.getId().equals(dummy.getId()));
        group.getMembers().add(currentUser);
        groupRepository.save(group);

        // Update ALL username references across the group
        userDebtRepository.findByGroupAndUsername(group, oldUsername).ifPresent(ud -> {
            ud.setUsername(newUsername);
            userDebtRepository.save(ud);
        });

        debtRepository.updateFromUser(group, oldUsername, newUsername);
        debtRepository.updateToUser(group, oldUsername, newUsername);
        optimisedDebtRepository.updateFromUser(group, oldUsername, newUsername);
        optimisedDebtRepository.updateToUser(group, oldUsername, newUsername);
        expenseRepository.updateAuthor(group, oldUsername, newUsername);
        expenseRepository.updateLender(group, oldUsername, newUsername);
        expenseLenderRepository.updateUsernameByGroup(group.getId(), oldUsername, newUsername);
        expenseBorrowerRepository.updateUsernameByGroup(group.getId(), oldUsername, newUsername);
        expenseCommentRepository.updateAuthorByGroup(group.getId(), oldUsername, newUsername);
        activityLogRepository.updateUser(group, oldUsername, newUsername);

        // Delete dummy user if not in any other groups
        List<ExpenseGroup> allGroups = groupRepository.findByMembersContainingOrderByCreatedAtDesc(dummy);
        if (allGroups.isEmpty()) {
            userRepository.delete(dummy);
        }

        // Log activity
        ActivityLog log = ActivityLog.builder()
                .group(group)
                .user(newUsername)
                .action("member_added")
                .description(newUsername + " claimed " + oldUsername + "'s spot via invite link")
                .build();
        activityLogRepository.save(log);

        return ResponseEntity.ok(Map.of(
                "message", "Successfully joined as " + newUsername,
                "group", GroupDto.from(group)
        ));
    }
}
