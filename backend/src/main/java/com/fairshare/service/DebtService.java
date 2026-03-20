package com.fairshare.service;

import com.fairshare.entity.*;
import com.fairshare.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
public class DebtService {

    private final UserDebtRepository userDebtRepository;
    private final DebtRepository debtRepository;
    private final OptimisedDebtRepository optimisedDebtRepository;

    public DebtService(UserDebtRepository userDebtRepository, DebtRepository debtRepository,
                       OptimisedDebtRepository optimisedDebtRepository) {
        this.userDebtRepository = userDebtRepository;
        this.debtRepository = debtRepository;
        this.optimisedDebtRepository = optimisedDebtRepository;
    }

    @Transactional
    public void processNewDebt(ExpenseGroup group, String fromUser, String toUser, int amount) {
        // Step 1: Update UserDebt
        UserDebt fromDebt = getOrCreateUserDebt(group, fromUser);
        fromDebt.setNetDebt(fromDebt.getNetDebt() + amount);
        userDebtRepository.save(fromDebt);

        UserDebt toDebt = getOrCreateUserDebt(group, toUser);
        toDebt.setNetDebt(toDebt.getNetDebt() - amount);
        userDebtRepository.save(toDebt);

        // Steps 2-3: Update pairwise debts
        addPairwiseDebt(group, fromUser, toUser, amount);
    }

    @Transactional
    public void reverseDebt(ExpenseGroup group, String fromUser, String toUser, int amount) {
        // Step 1: Reverse UserDebt
        UserDebt fromDebt = getOrCreateUserDebt(group, fromUser);
        fromDebt.setNetDebt(fromDebt.getNetDebt() - amount);
        userDebtRepository.save(fromDebt);

        UserDebt toDebt = getOrCreateUserDebt(group, toUser);
        toDebt.setNetDebt(toDebt.getNetDebt() + amount);
        userDebtRepository.save(toDebt);

        // Steps 2-3: Reverse pairwise debts
        reversePairwiseDebt(group, fromUser, toUser, amount);
    }

    @Transactional
    public void processMultiPayerDebt(ExpenseGroup group, List<String[]> lendersData,
                                       List<String[]> borrowersData, int totalAmount) {
        // Step 1: Update UserDebt for lenders and borrowers
        for (String[] lender : lendersData) {
            String username = lender[0];
            int lenderAmount = Integer.parseInt(lender[1]);
            UserDebt ud = getOrCreateUserDebt(group, username);
            ud.setNetDebt(ud.getNetDebt() - lenderAmount);
            userDebtRepository.save(ud);
        }
        for (String[] borrower : borrowersData) {
            String username = borrower[0];
            int borrowerAmount = Integer.parseInt(borrower[1]);
            UserDebt ud = getOrCreateUserDebt(group, username);
            ud.setNetDebt(ud.getNetDebt() + borrowerAmount);
            userDebtRepository.save(ud);
        }

        // Step 2: Create proportional pairwise debts
        for (String[] borrower : borrowersData) {
            String borrowerUsername = borrower[0];
            int borrowerAmount = Integer.parseInt(borrower[1]);
            for (String[] lender : lendersData) {
                String lenderUsername = lender[0];
                int lenderAmount = Integer.parseInt(lender[1]);
                if (borrowerUsername.equals(lenderUsername)) continue;
                int pairAmount = (int) Math.round((double) borrowerAmount * lenderAmount / totalAmount);
                if (pairAmount > 0) {
                    addPairwiseDebt(group, borrowerUsername, lenderUsername, pairAmount);
                }
            }
        }
    }

    @Transactional
    public void reverseMultiPayerDebt(ExpenseGroup group, List<String[]> lendersData,
                                       List<String[]> borrowersData, int totalAmount) {
        // Step 1: Reverse UserDebt
        for (String[] lender : lendersData) {
            String username = lender[0];
            int lenderAmount = Integer.parseInt(lender[1]);
            UserDebt ud = getOrCreateUserDebt(group, username);
            ud.setNetDebt(ud.getNetDebt() + lenderAmount);
            userDebtRepository.save(ud);
        }
        for (String[] borrower : borrowersData) {
            String username = borrower[0];
            int borrowerAmount = Integer.parseInt(borrower[1]);
            UserDebt ud = getOrCreateUserDebt(group, username);
            ud.setNetDebt(ud.getNetDebt() - borrowerAmount);
            userDebtRepository.save(ud);
        }

        // Step 2: Reverse proportional pairwise debts
        for (String[] borrower : borrowersData) {
            String borrowerUsername = borrower[0];
            int borrowerAmount = Integer.parseInt(borrower[1]);
            for (String[] lender : lendersData) {
                String lenderUsername = lender[0];
                int lenderAmount = Integer.parseInt(lender[1]);
                if (borrowerUsername.equals(lenderUsername)) continue;
                int pairAmount = (int) Math.round((double) borrowerAmount * lenderAmount / totalAmount);
                if (pairAmount > 0) {
                    reversePairwiseDebt(group, borrowerUsername, lenderUsername, pairAmount);
                }
            }
        }
    }

    @Transactional
    public void simplifyDebts(ExpenseGroup group) {
        List<UserDebt> allDebts = userDebtRepository.findByGroup(group);
        optimisedDebtRepository.deleteByGroup(group);
        optimisedDebtRepository.flush();

        // Separate into debtors and creditors using min-heaps
        PriorityQueue<long[]> debtors = new PriorityQueue<>(Comparator.comparingLong(a -> a[0]));
        PriorityQueue<long[]> creditors = new PriorityQueue<>(Comparator.comparingLong(a -> a[0]));
        Map<Long, String> indexToUsername = new HashMap<>();
        long idx = 0;

        for (UserDebt ud : allDebts) {
            if (ud.getNetDebt() >= 100) {
                debtors.add(new long[]{ud.getNetDebt(), idx});
                indexToUsername.put(idx, ud.getUsername());
                idx++;
            } else if (ud.getNetDebt() <= -100) {
                creditors.add(new long[]{-ud.getNetDebt(), idx});
                indexToUsername.put(idx, ud.getUsername());
                idx++;
            }
        }

        while (!debtors.isEmpty() && !creditors.isEmpty()) {
            long[] debtor = debtors.poll();
            long[] creditor = creditors.poll();

            long debtAmount = debtor[0];
            long creditAmount = creditor[0];
            long transaction = Math.min(debtAmount, creditAmount);

            OptimisedDebt od = OptimisedDebt.builder()
                    .group(group)
                    .fromUser(indexToUsername.get(debtor[1]))
                    .toUser(indexToUsername.get(creditor[1]))
                    .amount((int) transaction)
                    .build();
            optimisedDebtRepository.save(od);

            long debtorRemainder = debtAmount - transaction;
            long creditorRemainder = creditAmount - transaction;

            if (debtorRemainder > 0) {
                debtors.add(new long[]{debtorRemainder, debtor[1]});
            }
            if (creditorRemainder > 0) {
                creditors.add(new long[]{creditorRemainder, creditor[1]});
            }
        }
    }

    private void addPairwiseDebt(ExpenseGroup group, String fromUser, String toUser, int amount) {
        // Check for reverse debt
        Optional<Debt> reverseOpt = debtRepository.findByGroupAndFromUserAndToUser(group, toUser, fromUser);
        if (reverseOpt.isPresent()) {
            Debt reverse = reverseOpt.get();
            if (reverse.getAmount() > amount) {
                reverse.setAmount(reverse.getAmount() - amount);
                debtRepository.save(reverse);
                return;
            } else {
                int remaining = amount - reverse.getAmount();
                debtRepository.delete(reverse);
                amount = remaining;
            }
        }

        if (amount <= 0) return;

        // Create or update forward debt
        Optional<Debt> forwardOpt = debtRepository.findByGroupAndFromUserAndToUser(group, fromUser, toUser);
        if (forwardOpt.isPresent()) {
            Debt forward = forwardOpt.get();
            forward.setAmount(forward.getAmount() + amount);
            debtRepository.save(forward);
        } else {
            Debt newDebt = Debt.builder()
                    .group(group)
                    .fromUser(fromUser)
                    .toUser(toUser)
                    .amount(amount)
                    .build();
            debtRepository.save(newDebt);
        }
    }

    private void reversePairwiseDebt(ExpenseGroup group, String fromUser, String toUser, int amount) {
        // Check for existing forward debt
        Optional<Debt> forwardOpt = debtRepository.findByGroupAndFromUserAndToUser(group, fromUser, toUser);
        if (forwardOpt.isPresent()) {
            Debt forward = forwardOpt.get();
            if (forward.getAmount() > amount) {
                forward.setAmount(forward.getAmount() - amount);
                debtRepository.save(forward);
                return;
            } else {
                int remaining = amount - forward.getAmount();
                debtRepository.delete(forward);
                amount = remaining;
            }
        }

        if (amount <= 0) return;

        // Create or update reverse debt
        Optional<Debt> reverseOpt = debtRepository.findByGroupAndFromUserAndToUser(group, toUser, fromUser);
        if (reverseOpt.isPresent()) {
            Debt reverse = reverseOpt.get();
            reverse.setAmount(reverse.getAmount() + amount);
            debtRepository.save(reverse);
        } else {
            Debt newDebt = Debt.builder()
                    .group(group)
                    .fromUser(toUser)
                    .toUser(fromUser)
                    .amount(amount)
                    .build();
            debtRepository.save(newDebt);
        }
    }

    private UserDebt getOrCreateUserDebt(ExpenseGroup group, String username) {
        return userDebtRepository.findByGroupAndUsername(group, username)
                .orElseGet(() -> {
                    UserDebt ud = UserDebt.builder()
                            .group(group)
                            .username(username)
                            .netDebt(0)
                            .build();
                    return userDebtRepository.save(ud);
                });
    }
}
