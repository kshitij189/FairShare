package com.fairshare.dto;

import lombok.*;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class AuthResponse {
    private String access;
    private String refresh;
    private UserDto user;
}
