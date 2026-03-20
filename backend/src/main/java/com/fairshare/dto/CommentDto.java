package com.fairshare.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fairshare.entity.ExpenseComment;
import lombok.*;

import java.time.Instant;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class CommentDto {
    private Long id;
    private Long expense;
    private String author;
    private String text;

    @JsonProperty("created_at")
    private Instant createdAt;

    public static CommentDto from(ExpenseComment comment) {
        return CommentDto.builder()
                .id(comment.getId())
                .expense(comment.getExpense().getId())
                .author(comment.getAuthor())
                .text(comment.getText())
                .createdAt(comment.getCreatedAt())
                .build();
    }
}
