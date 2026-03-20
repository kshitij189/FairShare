package com.fairshare.controller;

import com.fairshare.dto.*;
import com.fairshare.entity.*;
import com.fairshare.repository.*;
import com.fairshare.service.GeminiService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/groups")
public class GroupController {

    private final GroupRepository groupRepository;
    private final UserRepository userRepository;
    private final UserDebtRepository userDebtRepository;
    private final ActivityLogRepository activityLogRepository;
    private final GeminiService geminiService;
    private final ExpenseRepository expenseRepository;
    private final OptimisedDebtRepository optimisedDebtRepository;

    public GroupController(GroupRepository groupRepository, UserRepository userRepository,
                          UserDebtRepository userDebtRepository, ActivityLogRepository activityLogRepository,
                          GeminiService geminiService, ExpenseRepository expenseRepository,
                          OptimisedDebtRepository optimisedDebtRepository) {
        this.groupRepository = groupRepository;
        this.userRepository = userRepository;
        this.userDebtRepository = userDebtRepository;
        this.activityLogRepository = activityLogRepository;
        this.geminiService = geminiService;
        this.expenseRepository = expenseRepository;
        this.optimisedDebtRepository = optimisedDebtRepository;
    }

    @GetMapping
    public ResponseEntity<?> listGroups(@AuthenticationPrincipal User currentUser) {
        List<ExpenseGroup> groups = groupRepository.findByMembersContainingOrderByCreatedAtDesc(currentUser);
        List<GroupDto> dtos = groups.stream().map(GroupDto::from).collect(Collectors.toList());
        return ResponseEntity.ok(dtos);
    }

    @PostMapping
    public ResponseEntity<?> createGroup(@AuthenticationPrincipal User currentUser,
                                          @RequestBody Map<String, String> body) {
        String name = body.get("name");
        if (name == null || name.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Name is required"));
        }

        ExpenseGroup group = ExpenseGroup.builder()
                .name(name)
                .createdBy(currentUser)
                .build();
        group.prePersist();
        group.getMembers().add(currentUser);
        groupRepository.save(group);

        UserDebt userDebt = UserDebt.builder()
                .group(group)
                .username(currentUser.getUsername())
                .netDebt(0)
                .build();
        userDebtRepository.save(userDebt);

        logActivity(group, currentUser.getUsername(), "group_created",
                "Created group '" + name + "'");

        return ResponseEntity.status(HttpStatus.CREATED).body(GroupDto.from(group));
    }

    @GetMapping("/{groupId}")
    public ResponseEntity<?> getGroup(@AuthenticationPrincipal User currentUser,
                                       @PathVariable Long groupId) {
        return withGroupMembership(groupId, currentUser, group ->
                ResponseEntity.ok(GroupDto.from(group)));
    }

    @DeleteMapping("/{groupId}")
    public ResponseEntity<?> deleteGroup(@AuthenticationPrincipal User currentUser,
                                          @PathVariable Long groupId) {
        return withGroupMembership(groupId, currentUser, group -> {
            if (!group.getCreatedBy().getId().equals(currentUser.getId())) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Map.of("error", "Only the creator can delete the group"));
            }
            groupRepository.delete(group);
            return ResponseEntity.noContent().build();
        });
    }

    @PostMapping("/{groupId}/members")
    public ResponseEntity<?> addMember(@AuthenticationPrincipal User currentUser,
                                        @PathVariable Long groupId,
                                        @RequestBody Map<String, String> body) {
        String username = body.get("username");
        if (username == null || username.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Username is required"));
        }
        String memberUsername = username.strip();

        return withGroupMembership(groupId, currentUser, group -> {
            // Find or create user
            User memberUser = userRepository.findByUsernameIgnoreCase(memberUsername).orElse(null);
            if (memberUser == null) {
                memberUser = User.builder()
                        .username(memberUsername)
                        .firstName(memberUsername)
                        .lastName("")
                        .email("")
                        .hasUsablePassword(false)
                        .build();
                userRepository.save(memberUser);
            }

            if (group.getMembers().contains(memberUser)) {
                return ResponseEntity.badRequest().body(Map.of("error", "User is already a member"));
            }

            group.getMembers().add(memberUser);
            groupRepository.save(group);

            UserDebt userDebt = UserDebt.builder()
                    .group(group)
                    .username(memberUser.getUsername())
                    .netDebt(0)
                    .build();
            userDebtRepository.save(userDebt);

            logActivity(group, currentUser.getUsername(), "member_added",
                    "Added " + memberUser.getUsername() + " to the group");

            return ResponseEntity.ok(GroupDto.from(group));
        });
    }

    @GetMapping("/{groupId}/users")
    public ResponseEntity<?> listUsers(@AuthenticationPrincipal User currentUser,
                                        @PathVariable Long groupId) {
        return withGroupMembership(groupId, currentUser, group -> {
            List<UserDto> users = group.getMembers().stream()
                    .map(UserDto::from)
                    .collect(Collectors.toList());
            return ResponseEntity.ok(users);
        });
    }

    @GetMapping("/{groupId}/activity")
    public ResponseEntity<?> listActivity(@AuthenticationPrincipal User currentUser,
                                           @PathVariable Long groupId) {
        return withGroupMembership(groupId, currentUser, group -> {
            List<ActivityLogDto> logs = activityLogRepository.findByGroupOrderByCreatedAtDesc(group)
                    .stream().map(ActivityLogDto::from).collect(Collectors.toList());
            return ResponseEntity.ok(logs);
        });
    }

    @PostMapping("/{groupId}/ai-chat")
    public ResponseEntity<?> aiChat(@AuthenticationPrincipal User currentUser,
                                     @PathVariable Long groupId,
                                     @RequestBody Map<String, String> body) {
        return withGroupMembership(groupId, currentUser, group -> {
            String message = body.get("message");
            Map<String, Object> context = buildAiContext(group);
            String reply = geminiService.getBotResponse(message, context);
            return ResponseEntity.ok(Map.of("reply", reply));
        });
    }

    // Helper: verify group membership
    private ResponseEntity<?> withGroupMembership(Long groupId, User user,
                                                   java.util.function.Function<ExpenseGroup, ResponseEntity<?>> action) {
        Optional<ExpenseGroup> groupOpt = groupRepository.findById(groupId);
        if (groupOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        ExpenseGroup group = groupOpt.get();
        boolean isMember = group.getMembers().stream()
                .anyMatch(m -> m.getId().equals(user.getId()));
        if (!isMember) {
            return ResponseEntity.notFound().build();
        }
        return action.apply(group);
    }

    private void logActivity(ExpenseGroup group, String user, String action, String description) {
        ActivityLog log = ActivityLog.builder()
                .group(group)
                .user(user)
                .action(action)
                .description(description)
                .build();
        activityLogRepository.save(log);
    }

    private Map<String, Object> buildAiContext(ExpenseGroup group) {
        Map<String, Object> balances = new LinkedHashMap<>();
        for (UserDebt ud : userDebtRepository.findByGroup(group)) {
            balances.put(ud.getUsername(), ud.getNetDebt());
        }

        List<Expense> expenses = expenseRepository.findTop100ByGroupOrderByCreatedAtDesc(group);
        List<Map<String, Object>> recentExpenses = expenses.stream().map(e -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("title", e.getTitle());
            m.put("amount", e.getAmount());
            m.put("lender", e.getLender());
            m.put("date", e.getCreatedAt().toString().substring(0, 10));
            return m;
        }).collect(Collectors.toList());

        List<Map<String, Object>> settlements = optimisedDebtRepository.findByGroup(group).stream().map(od -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("fromUser", od.getFromUser());
            m.put("toUser", od.getToUser());
            m.put("amount", od.getAmount());
            return m;
        }).collect(Collectors.toList());

        Map<String, Object> context = new HashMap<>();
        context.put("balances", balances);
        context.put("recent_expenses", recentExpenses);
        context.put("settlements", settlements);
        return context;
    }
}
