package com.fairshare.controller;

import com.fairshare.dto.DebtDto;
import com.fairshare.entity.*;
import com.fairshare.repository.*;
import com.fairshare.service.DebtService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/groups/{groupId}")
public class DebtController {

    private final GroupRepository groupRepository;
    private final DebtRepository debtRepository;
    private final OptimisedDebtRepository optimisedDebtRepository;
    private final ActivityLogRepository activityLogRepository;
    private final DebtService debtService;

    public DebtController(GroupRepository groupRepository, DebtRepository debtRepository,
                         OptimisedDebtRepository optimisedDebtRepository,
                         ActivityLogRepository activityLogRepository, DebtService debtService) {
        this.groupRepository = groupRepository;
        this.debtRepository = debtRepository;
        this.optimisedDebtRepository = optimisedDebtRepository;
        this.activityLogRepository = activityLogRepository;
        this.debtService = debtService;
    }

    @GetMapping("/debts")
    public ResponseEntity<?> listDebts(@AuthenticationPrincipal User currentUser,
                                        @PathVariable Long groupId) {
        return withGroupMembership(groupId, currentUser, group -> {
            List<DebtDto> debts = debtRepository.findByGroup(group).stream()
                    .map(DebtDto::from).collect(Collectors.toList());
            return ResponseEntity.ok(debts);
        });
    }

    @GetMapping("/optimisedDebts")
    public ResponseEntity<?> listOptimisedDebts(@AuthenticationPrincipal User currentUser,
                                                 @PathVariable Long groupId) {
        return withGroupMembership(groupId, currentUser, group -> {
            List<DebtDto> debts = optimisedDebtRepository.findByGroup(group).stream()
                    .map(DebtDto::from).collect(Collectors.toList());
            return ResponseEntity.ok(debts);
        });
    }

    @GetMapping("/debts/{fromUser}/{toUser}")
    public ResponseEntity<?> getDebt(@AuthenticationPrincipal User currentUser,
                                      @PathVariable Long groupId,
                                      @PathVariable String fromUser,
                                      @PathVariable String toUser) {
        return withGroupMembership(groupId, currentUser, group -> {
            Optional<Debt> debtOpt = debtRepository.findByGroupAndFromUserAndToUser(group, fromUser, toUser);
            if (debtOpt.isEmpty()) return ResponseEntity.ok(null);
            return ResponseEntity.ok(DebtDto.from(debtOpt.get()));
        });
    }

    @DeleteMapping("/debts/{fromUser}/{toUser}")
    @Transactional
    public ResponseEntity<?> deleteDebt(@AuthenticationPrincipal User currentUser,
                                         @PathVariable Long groupId,
                                         @PathVariable String fromUser,
                                         @PathVariable String toUser) {
        return withGroupMembership(groupId, currentUser, group -> {
            Optional<Debt> debtOpt = debtRepository.findByGroupAndFromUserAndToUser(group, fromUser, toUser);
            if (debtOpt.isPresent()) {
                debtRepository.delete(debtOpt.get());
                debtService.simplifyDebts(group);
            }
            return ResponseEntity.ok("Debt from '" + fromUser + "' to '" + toUser + "' deleted successfully.");
        });
    }

    @PostMapping("/debts/add")
    @Transactional
    public ResponseEntity<?> addDebt(@AuthenticationPrincipal User currentUser,
                                      @PathVariable Long groupId,
                                      @RequestBody Map<String, Object> body) {
        return withGroupMembership(groupId, currentUser, group -> {
            String from = (String) body.get("from");
            String to = (String) body.get("to");
            int amount = toInt(body.get("amount"));

            debtService.processNewDebt(group, from, to, amount);
            debtService.simplifyDebts(group);

            return ResponseEntity.status(HttpStatus.CREATED)
                    .body("Debt added successfully.");
        });
    }

    @PostMapping("/debts/settle")
    @Transactional
    public ResponseEntity<?> settleDebt(@AuthenticationPrincipal User currentUser,
                                         @PathVariable Long groupId,
                                         @RequestBody Map<String, Object> body) {
        return withGroupMembership(groupId, currentUser, group -> {
            String from = (String) body.get("from");
            String to = (String) body.get("to");

            int amount;
            Object amountObj = body.get("amount");
            if (amountObj instanceof String) {
                amount = (int) (Double.parseDouble((String) amountObj) * 100);
            } else {
                amount = toInt(amountObj);
            }

            if (amount <= 0) {
                return ResponseEntity.badRequest().body(Map.of("error", "Amount must be greater than 0."));
            }

            debtService.reverseDebt(group, from, to, amount);
            debtService.simplifyDebts(group);

            String desc = String.format("%s paid %s %.2f", from, to, amount / 100.0);
            activityLogRepository.save(ActivityLog.builder()
                    .group(group).user(currentUser.getUsername())
                    .action("settlement").description(desc).build());

            return ResponseEntity.ok("Settlement recorded successfully.");
        });
    }

    // --- Helpers ---

    private ResponseEntity<?> withGroupMembership(Long groupId, User user,
                                                   java.util.function.Function<ExpenseGroup, ResponseEntity<?>> action) {
        Optional<ExpenseGroup> groupOpt = groupRepository.findById(groupId);
        if (groupOpt.isEmpty()) return ResponseEntity.notFound().build();
        ExpenseGroup group = groupOpt.get();
        boolean isMember = group.getMembers().stream().anyMatch(m -> m.getId().equals(user.getId()));
        if (!isMember) return ResponseEntity.notFound().build();
        return action.apply(group);
    }

    private int toInt(Object val) {
        if (val instanceof Integer) return (Integer) val;
        if (val instanceof Long) return ((Long) val).intValue();
        if (val instanceof Double) return (int) Math.round((Double) val);
        if (val instanceof String) return (int) Math.round(Double.parseDouble((String) val));
        return Integer.parseInt(val.toString());
    }
}
