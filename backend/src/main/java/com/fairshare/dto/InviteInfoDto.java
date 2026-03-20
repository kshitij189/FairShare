package com.fairshare.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

import java.util.List;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class InviteInfoDto {

    @JsonProperty("group_id")
    private Long groupId;

    @JsonProperty("group_name")
    private String groupName;

    @JsonProperty("invite_code")
    private String inviteCode;

    private List<InviteMemberDto> members;

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class InviteMemberDto {
        private Long id;
        private String username;

        @JsonProperty("is_dummy")
        private boolean isDummy;
    }
}
