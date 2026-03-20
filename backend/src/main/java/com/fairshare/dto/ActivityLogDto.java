package com.fairshare.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fairshare.entity.ActivityLog;
import lombok.*;

import java.time.Instant;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ActivityLogDto {
    private Long id;
    private Long group;
    private String user;
    private String action;
    private String description;

    @JsonProperty("created_at")
    private Instant createdAt;

    public static ActivityLogDto from(ActivityLog log) {
        return ActivityLogDto.builder()
                .id(log.getId())
                .group(log.getGroup().getId())
                .user(log.getUser())
                .action(log.getAction())
                .description(log.getDescription())
                .createdAt(log.getCreatedAt())
                .build();
    }
}
