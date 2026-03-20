package com.fairshare.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fairshare.entity.*;
import lombok.*;

import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ExpenseDto {
    private Long id;
    private Long group;
    private String title;
    private String author;
    private String lender;
    private List<LenderBorrowerDto> lenders;
    private List<LenderBorrowerDto> borrowers;
    private List<CommentDto> comments;
    private int amount;

    @JsonProperty("created_at")
    private Instant createdAt;

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class LenderBorrowerDto {
        private String username;
        private int amount;
    }

    public static ExpenseDto from(Expense expense, List<ExpenseLender> lenders,
                                   List<ExpenseBorrower> borrowers, List<ExpenseComment> comments) {
        return ExpenseDto.builder()
                .id(expense.getId())
                .group(expense.getGroup().getId())
                .title(expense.getTitle())
                .author(expense.getAuthor())
                .lender(expense.getLender())
                .lenders(lenders.stream()
                        .map(l -> new LenderBorrowerDto(l.getUsername(), l.getAmount()))
                        .collect(Collectors.toList()))
                .borrowers(borrowers.stream()
                        .map(b -> new LenderBorrowerDto(b.getUsername(), b.getAmount()))
                        .collect(Collectors.toList()))
                .comments(comments.stream()
                        .map(CommentDto::from)
                        .collect(Collectors.toList()))
                .amount(expense.getAmount())
                .createdAt(expense.getCreatedAt())
                .build();
    }
}
