package com.fairshare.controller;

import com.fairshare.dto.CommentDto;
import com.fairshare.dto.ExpenseDto;
import com.fairshare.entity.*;
import com.fairshare.repository.*;
import com.fairshare.service.DebtService;
import com.fairshare.service.GeminiService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/groups/{groupId}/expenses")
public class ExpenseController {

    private final GroupRepository groupRepository;
    private final ExpenseRepository expenseRepository;
    private final ExpenseLenderRepository expenseLenderRepository;
    private final ExpenseBorrowerRepository expenseBorrowerRepository;
    private final ExpenseCommentRepository expenseCommentRepository;
    private final ActivityLogRepository activityLogRepository;
    private final UserDebtRepository userDebtRepository;
    private final DebtService debtService;
    private final GeminiService geminiService;
    private final OptimisedDebtRepository optimisedDebtRepository;

    public ExpenseController(GroupRepository groupRepository, ExpenseRepository expenseRepository,
                            ExpenseLenderRepository expenseLenderRepository, ExpenseBorrowerRepository expenseBorrowerRepository,
                            ExpenseCommentRepository expenseCommentRepository, ActivityLogRepository activityLogRepository,
                            UserDebtRepository userDebtRepository, DebtService debtService, GeminiService geminiService,
                            OptimisedDebtRepository optimisedDebtRepository) {
        this.groupRepository = groupRepository;
        this.expenseRepository = expenseRepository;
        this.expenseLenderRepository = expenseLenderRepository;
        this.expenseBorrowerRepository = expenseBorrowerRepository;
        this.expenseCommentRepository = expenseCommentRepository;
        this.activityLogRepository = activityLogRepository;
        this.userDebtRepository = userDebtRepository;
        this.debtService = debtService;
        this.geminiService = geminiService;
        this.optimisedDebtRepository = optimisedDebtRepository;
    }

    @GetMapping
    public ResponseEntity<?> listExpenses(@AuthenticationPrincipal User currentUser,
                                           @PathVariable Long groupId) {
        return withGroupMembership(groupId, currentUser, group -> {
            List<Expense> expenses = expenseRepository.findByGroupOrderByCreatedAtDesc(group);
            List<ExpenseDto> dtos = expenses.stream()
                    .map(e -> toDto(e))
                    .collect(Collectors.toList());
            return ResponseEntity.ok(dtos);
        });
    }

    @PostMapping
    @Transactional
    public ResponseEntity<?> createExpense(@AuthenticationPrincipal User currentUser,
                                            @PathVariable Long groupId,
                                            @RequestBody Map<String, Object> body) {
        return withGroupMembership(groupId, currentUser, group -> {
            String title = (String) body.get("title");
            int amount = toInt(body.get("amount"));

            List<String[]> lendersData = parseLendersBorrowers(body.get("lenders"));
            List<String[]> borrowersData = parseLendersBorrowers(body.get("borrowers"));

            // Legacy single-lender fallback
            if (lendersData.isEmpty() && body.containsKey("lender")) {
                String lender = ((String) body.get("lender")).toLowerCase();
                lendersData.add(new String[]{lender, String.valueOf(amount)});
            }

            // Validate sums
            int lenderSum = lendersData.stream().mapToInt(l -> Integer.parseInt(l[1])).sum();
            int borrowerSum = borrowersData.stream().mapToInt(b -> Integer.parseInt(b[1])).sum();
            if (lenderSum != amount || borrowerSum != amount) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "Lender or borrower amounts do not add up to the total amount."));
            }

            String primaryLender = lendersData.get(0)[0].toLowerCase();

            Expense expense = Expense.builder()
                    .group(group)
                    .title(title)
                    .author(currentUser.getUsername())
                    .lender(primaryLender)
                    .amount(amount)
                    .build();
            expenseRepository.save(expense);

            // Create ExpenseLender records
            for (String[] ld : lendersData) {
                ExpenseLender el = ExpenseLender.builder()
                        .expense(expense)
                        .username(ld[0].toLowerCase())
                        .amount(Integer.parseInt(ld[1]))
                        .build();
                expenseLenderRepository.save(el);
            }

            // Create ExpenseBorrower records
            for (String[] bd : borrowersData) {
                ExpenseBorrower eb = ExpenseBorrower.builder()
                        .expense(expense)
                        .username(bd[0].toLowerCase())
                        .amount(Integer.parseInt(bd[1]))
                        .build();
                expenseBorrowerRepository.save(eb);
            }

            // Process debts
            debtService.processMultiPayerDebt(group, lendersData, borrowersData, amount);
            debtService.simplifyDebts(group);

            // Log activity
            String desc = String.format("Added expense '%s' for %.2f", title, amount / 100.0);
            if (lendersData.size() > 1) desc += " (multi-payer)";
            logActivity(group, currentUser.getUsername(), "expense_added", desc);

            return ResponseEntity.status(HttpStatus.CREATED).body(toDto(expense));
        });
    }

    @GetMapping("/{expenseId}")
    public ResponseEntity<?> getExpense(@AuthenticationPrincipal User currentUser,
                                         @PathVariable Long groupId,
                                         @PathVariable Long expenseId) {
        return withGroupMembership(groupId, currentUser, group -> {
            Optional<Expense> expOpt = expenseRepository.findByIdAndGroup(expenseId, group);
            if (expOpt.isEmpty()) return ResponseEntity.notFound().build();
            return ResponseEntity.ok(toDto(expOpt.get()));
        });
    }

    @DeleteMapping("/{expenseId}")
    @Transactional
    public ResponseEntity<?> deleteExpense(@AuthenticationPrincipal User currentUser,
                                            @PathVariable Long groupId,
                                            @PathVariable Long expenseId) {
        return withGroupMembership(groupId, currentUser, group -> {
            Optional<Expense> expOpt = expenseRepository.findByIdAndGroup(expenseId, group);
            if (expOpt.isEmpty()) return ResponseEntity.notFound().build();

            Expense expense = expOpt.get();
            List<String[]> lendersData = expenseLenderRepository.findByExpense(expense).stream()
                    .map(l -> new String[]{l.getUsername(), String.valueOf(l.getAmount())})
                    .collect(Collectors.toList());
            List<String[]> borrowersData = expenseBorrowerRepository.findByExpense(expense).stream()
                    .map(b -> new String[]{b.getUsername(), String.valueOf(b.getAmount())})
                    .collect(Collectors.toList());

            debtService.reverseMultiPayerDebt(group, lendersData, borrowersData, expense.getAmount());

            String title = expense.getTitle();
            expenseCommentRepository.findByExpenseOrderByCreatedAtAsc(expense)
                    .forEach(expenseCommentRepository::delete);
            expenseLenderRepository.deleteByExpense(expense);
            expenseBorrowerRepository.deleteByExpense(expense);
            expenseRepository.delete(expense);

            debtService.simplifyDebts(group);

            logActivity(group, currentUser.getUsername(), "expense_deleted",
                    "Deleted expense '" + title + "'");

            return ResponseEntity.ok("Expense deleted successfully.");
        });
    }

    @PutMapping("/{expenseId}")
    @Transactional
    public ResponseEntity<?> editExpense(@AuthenticationPrincipal User currentUser,
                                          @PathVariable Long groupId,
                                          @PathVariable Long expenseId,
                                          @RequestBody Map<String, Object> body) {
        return withGroupMembership(groupId, currentUser, group -> {
            Optional<Expense> expOpt = expenseRepository.findByIdAndGroup(expenseId, group);
            if (expOpt.isEmpty()) return ResponseEntity.notFound().build();

            Expense expense = expOpt.get();

            // Get old lenders/borrowers
            List<String[]> oldLenders = expenseLenderRepository.findByExpense(expense).stream()
                    .map(l -> new String[]{l.getUsername(), String.valueOf(l.getAmount())})
                    .collect(Collectors.toList());
            List<String[]> oldBorrowers = expenseBorrowerRepository.findByExpense(expense).stream()
                    .map(b -> new String[]{b.getUsername(), String.valueOf(b.getAmount())})
                    .collect(Collectors.toList());

            // Reverse old debts
            debtService.reverseMultiPayerDebt(group, oldLenders, oldBorrowers, expense.getAmount());

            // Delete old records
            expenseLenderRepository.deleteByExpense(expense);
            expenseBorrowerRepository.deleteByExpense(expense);

            // Parse new data
            String title = (String) body.get("title");
            int amount = toInt(body.get("amount"));
            List<String[]> newLenders = parseLendersBorrowers(body.get("lenders"));
            List<String[]> newBorrowers = parseLendersBorrowers(body.get("borrowers"));

            if (newLenders.isEmpty() && body.containsKey("lender")) {
                String lender = ((String) body.get("lender")).toLowerCase();
                newLenders.add(new String[]{lender, String.valueOf(amount)});
            }

            // Validate
            int lenderSum = newLenders.stream().mapToInt(l -> Integer.parseInt(l[1])).sum();
            int borrowerSum = newBorrowers.stream().mapToInt(b -> Integer.parseInt(b[1])).sum();
            if (lenderSum != amount || borrowerSum != amount) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "Lender or borrower amounts do not add up to the total amount."));
            }

            // Update expense
            expense.setTitle(title);
            expense.setAmount(amount);
            expense.setLender(newLenders.get(0)[0].toLowerCase());
            expenseRepository.save(expense);

            // Create new records
            for (String[] ld : newLenders) {
                expenseLenderRepository.save(ExpenseLender.builder()
                        .expense(expense).username(ld[0].toLowerCase()).amount(Integer.parseInt(ld[1])).build());
            }
            for (String[] bd : newBorrowers) {
                expenseBorrowerRepository.save(ExpenseBorrower.builder()
                        .expense(expense).username(bd[0].toLowerCase()).amount(Integer.parseInt(bd[1])).build());
            }

            // Process new debts
            debtService.processMultiPayerDebt(group, newLenders, newBorrowers, amount);
            debtService.simplifyDebts(group);

            logActivity(group, currentUser.getUsername(), "expense_edited",
                    "Edited expense '" + title + "'");

            return ResponseEntity.ok(toDto(expense));
        });
    }

    @GetMapping("/{expenseId}/comments")
    public ResponseEntity<?> listComments(@AuthenticationPrincipal User currentUser,
                                           @PathVariable Long groupId,
                                           @PathVariable Long expenseId) {
        return withGroupMembership(groupId, currentUser, group -> {
            Optional<Expense> expOpt = expenseRepository.findByIdAndGroup(expenseId, group);
            if (expOpt.isEmpty()) return ResponseEntity.notFound().build();

            List<CommentDto> comments = expenseCommentRepository.findByExpenseOrderByCreatedAtAsc(expOpt.get())
                    .stream().map(CommentDto::from).collect(Collectors.toList());
            return ResponseEntity.ok(comments);
        });
    }

    @PostMapping("/{expenseId}/comments")
    @Transactional
    public ResponseEntity<?> addComment(@AuthenticationPrincipal User currentUser,
                                         @PathVariable Long groupId,
                                         @PathVariable Long expenseId,
                                         @RequestBody Map<String, String> body) {
        return withGroupMembership(groupId, currentUser, group -> {
            Optional<Expense> expOpt = expenseRepository.findByIdAndGroup(expenseId, group);
            if (expOpt.isEmpty()) return ResponseEntity.notFound().build();

            Expense expense = expOpt.get();
            String text = body.get("text");

            ExpenseComment comment = ExpenseComment.builder()
                    .expense(expense)
                    .author(currentUser.getUsername())
                    .text(text)
                    .build();
            expenseCommentRepository.save(comment);

            // If text starts with @SplitBot, generate bot response
            if (text != null && text.startsWith("@SplitBot")) {
                String query = text.substring("@SplitBot".length()).trim();
                Map<String, Object> context = buildAiContext(group);
                String botReply = geminiService.getBotResponse(query, context);

                ExpenseComment botComment = ExpenseComment.builder()
                        .expense(expense)
                        .author("SplitBot")
                        .text(botReply)
                        .build();
                expenseCommentRepository.save(botComment);
            }

            logActivity(group, currentUser.getUsername(), "expense_edited",
                    "Commented on '" + expense.getTitle() + "'");

            List<CommentDto> allComments = expenseCommentRepository.findByExpenseOrderByCreatedAtAsc(expense)
                    .stream().map(CommentDto::from).collect(Collectors.toList());
            return ResponseEntity.status(HttpStatus.CREATED).body(allComments);
        });
    }

    @DeleteMapping("/{expenseId}/comments/{commentId}")
    @Transactional
    public ResponseEntity<?> deleteComment(@AuthenticationPrincipal User currentUser,
                                            @PathVariable Long groupId,
                                            @PathVariable Long expenseId,
                                            @PathVariable Long commentId) {
        return withGroupMembership(groupId, currentUser, group -> {
            Optional<Expense> expOpt = expenseRepository.findByIdAndGroup(expenseId, group);
            if (expOpt.isEmpty()) return ResponseEntity.notFound().build();

            Expense expense = expOpt.get();
            Optional<ExpenseComment> commentOpt = expenseCommentRepository.findById(commentId);
            if (commentOpt.isEmpty()) return ResponseEntity.notFound().build();

            ExpenseComment comment = commentOpt.get();
            if (!comment.getAuthor().equals(currentUser.getUsername())) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body("You can only delete your own comments.");
            }

            String commentText = comment.getText();
            expenseCommentRepository.delete(comment);

            String truncatedText = commentText.length() > 20 ? commentText.substring(0, 20) + "..." : commentText;
            logActivity(group, currentUser.getUsername(), "comment_deleted",
                    "Deleted comment: '" + truncatedText + "' on '" + expense.getTitle() + "'");

            List<CommentDto> remaining = expenseCommentRepository.findByExpenseOrderByCreatedAtAsc(expense)
                    .stream().map(CommentDto::from).collect(Collectors.toList());
            return ResponseEntity.ok(remaining);
        });
    }

    @PostMapping("/settlement")
    @Transactional
    public ResponseEntity<?> settlement(@AuthenticationPrincipal User currentUser,
                                         @PathVariable Long groupId,
                                         @RequestBody Map<String, Object> body) {
        return withGroupMembership(groupId, currentUser, group -> {
            String title = (String) body.get("title");
            String lender = (String) body.get("lender");
            int amount = toInt(body.get("amount"));

            Expense expense = Expense.builder()
                    .group(group)
                    .title(title)
                    .author(currentUser.getUsername())
                    .lender(lender.toLowerCase())
                    .amount(amount)
                    .build();
            expenseRepository.save(expense);

            List<Object> borrowersList = (List<Object>) body.get("borrowers");
            if (borrowersList != null) {
                for (Object item : borrowersList) {
                    String username;
                    int borrowerAmount;
                    if (item instanceof List) {
                        List<?> pair = (List<?>) item;
                        username = pair.get(0).toString().toLowerCase();
                        borrowerAmount = toInt(pair.get(1));
                    } else {
                        Map<String, Object> map = (Map<String, Object>) item;
                        username = map.get("username").toString().toLowerCase();
                        borrowerAmount = toInt(map.get("amount"));
                    }
                    expenseBorrowerRepository.save(ExpenseBorrower.builder()
                            .expense(expense).username(username).amount(borrowerAmount).build());
                }
            }

            logActivity(group, currentUser.getUsername(), "settlement", title);

            return ResponseEntity.status(HttpStatus.CREATED).body(toDto(expense));
        });
    }

    // --- Helpers ---

    private ExpenseDto toDto(Expense expense) {
        List<ExpenseLender> lenders = expenseLenderRepository.findByExpense(expense);
        List<ExpenseBorrower> borrowers = expenseBorrowerRepository.findByExpense(expense);
        List<ExpenseComment> comments = expenseCommentRepository.findByExpenseOrderByCreatedAtAsc(expense);
        return ExpenseDto.from(expense, lenders, borrowers, comments);
    }

    @SuppressWarnings("unchecked")
    private List<String[]> parseLendersBorrowers(Object data) {
        List<String[]> result = new ArrayList<>();
        if (data == null) return result;
        List<Object> list = (List<Object>) data;
        for (Object item : list) {
            if (item instanceof List) {
                List<?> pair = (List<?>) item;
                result.add(new String[]{pair.get(0).toString().toLowerCase(), String.valueOf(toInt(pair.get(1)))});
            } else if (item instanceof Map) {
                Map<String, Object> map = (Map<String, Object>) item;
                result.add(new String[]{map.get("username").toString().toLowerCase(),
                        String.valueOf(toInt(map.get("amount")))});
            }
        }
        return result;
    }

    private int toInt(Object val) {
        if (val instanceof Integer) return (Integer) val;
        if (val instanceof Long) return ((Long) val).intValue();
        if (val instanceof Double) return (int) Math.round((Double) val);
        if (val instanceof String) return (int) Math.round(Double.parseDouble((String) val));
        return Integer.parseInt(val.toString());
    }

    private ResponseEntity<?> withGroupMembership(Long groupId, User user,
                                                   java.util.function.Function<ExpenseGroup, ResponseEntity<?>> action) {
        Optional<ExpenseGroup> groupOpt = groupRepository.findById(groupId);
        if (groupOpt.isEmpty()) return ResponseEntity.notFound().build();
        ExpenseGroup group = groupOpt.get();
        boolean isMember = group.getMembers().stream().anyMatch(m -> m.getId().equals(user.getId()));
        if (!isMember) return ResponseEntity.notFound().build();
        return action.apply(group);
    }

    private void logActivity(ExpenseGroup group, String user, String action, String description) {
        activityLogRepository.save(ActivityLog.builder()
                .group(group).user(user).action(action).description(description).build());
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
