package com.fairshare.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fairshare.entity.ExpenseGroup;
import lombok.*;

import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class GroupDto {
    private Long id;
    private String name;

    @JsonProperty("created_by")
    private UserDto createdBy;

    private List<UserDto> members;

    @JsonProperty("created_at")
    private Instant createdAt;

    @JsonProperty("invite_code")
    private String inviteCode;

    public static GroupDto from(ExpenseGroup group) {
        return GroupDto.builder()
                .id(group.getId())
                .name(group.getName())
                .createdBy(UserDto.from(group.getCreatedBy()))
                .members(group.getMembers().stream()
                        .map(UserDto::from)
                        .collect(Collectors.toList()))
                .createdAt(group.getCreatedAt())
                .inviteCode(group.getInviteCode())
                .build();
    }
}
