package com.fairshare.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fairshare.dto.AuthResponse;
import com.fairshare.dto.UserDto;
import com.fairshare.entity.ActivityLog;
import com.fairshare.entity.ExpenseGroup;
import com.fairshare.entity.User;
import com.fairshare.repository.ActivityLogRepository;
import com.fairshare.repository.GroupRepository;
import com.fairshare.repository.UserRepository;
import com.fairshare.security.JwtUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final UserRepository userRepository;
    private final GroupRepository groupRepository;
    private final ActivityLogRepository activityLogRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newHttpClient();

    @Value("${google.client-id:}")
    private String googleClientId;

    public AuthController(UserRepository userRepository, GroupRepository groupRepository,
                          ActivityLogRepository activityLogRepository, PasswordEncoder passwordEncoder, JwtUtil jwtUtil) {
        this.userRepository = userRepository;
        this.groupRepository = groupRepository;
        this.activityLogRepository = activityLogRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
    }

    @PostMapping("/signup")
    public ResponseEntity<?> signup(@RequestBody Map<String, String> body) {
        String username = body.get("username");
        String password = body.get("password");

        if (username == null || username.isBlank() || password == null || password.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Username and password required"));
        }

        username = username.toLowerCase();
        String email = body.getOrDefault("email", "");
        String firstName = body.getOrDefault("firstName", "");
        String lastName = body.getOrDefault("lastName", "");

        Optional<User> existingOpt = userRepository.findByUsername(username);
        User user;

        if (existingOpt.isPresent()) {
            user = existingOpt.get();
            if (user.isHasUsablePassword()) {
                return ResponseEntity.badRequest().body(Map.of("error", "Username already exists"));
            }
            // Claim dummy account
            user.setPassword(passwordEncoder.encode(password));
            user.setHasUsablePassword(true);
            if (!email.isEmpty()) user.setEmail(email);
            if (!firstName.isEmpty()) user.setFirstName(firstName);
            if (!lastName.isEmpty()) user.setLastName(lastName);
            userRepository.save(user);

            // Log activity in all groups they were already part of as a dummy
            List<ExpenseGroup> userGroups = groupRepository.findByMembersContainingOrderByCreatedAtDesc(user);
            for (ExpenseGroup group : userGroups) {
                ActivityLog log = ActivityLog.builder()
                        .group(group)
                        .user(user.getUsername())
                        .action("member_joined")
                        .description(user.getUsername() + " has officially joined FairShare!")
                        .build();
                activityLogRepository.save(log);
            }
        } else {
            user = User.builder()
                    .username(username)
                    .password(passwordEncoder.encode(password))
                    .email(email)
                    .firstName(firstName)
                    .lastName(lastName)
                    .hasUsablePassword(true)
                    .build();
            userRepository.save(user);
        }

        AuthResponse response = AuthResponse.builder()
                .access(jwtUtil.generateAccessToken(user.getUsername()))
                .refresh(jwtUtil.generateRefreshToken(user.getUsername()))
                .user(UserDto.from(user))
                .build();

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Map<String, String> body) {
        String username = body.get("username");
        String password = body.get("password");

        if (username == null || password == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Invalid credentials"));
        }

        username = username.toLowerCase();
        Optional<User> userOpt = userRepository.findByUsername(username);

        if (userOpt.isEmpty() || !userOpt.get().isHasUsablePassword()
                || !passwordEncoder.matches(password, userOpt.get().getPassword())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Invalid credentials"));
        }

        User user = userOpt.get();
        AuthResponse response = AuthResponse.builder()
                .access(jwtUtil.generateAccessToken(user.getUsername()))
                .refresh(jwtUtil.generateRefreshToken(user.getUsername()))
                .user(UserDto.from(user))
                .build();

        return ResponseEntity.ok(response);
    }

    @PostMapping("/google")
    public ResponseEntity<?> googleAuth(@RequestBody Map<String, String> body) {
        String credential = body.get("credential");
        String email = body.get("email");
        String givenName = body.getOrDefault("given_name", "");
        String familyName = body.getOrDefault("family_name", "");

        if (credential == null || credential.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Credential is required"));
        }

        try {
            if (email != null && !email.isBlank()) {
                // Verify access token
                String tokenInfoUrl = "https://oauth2.googleapis.com/tokeninfo?access_token=" + credential;
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(tokenInfoUrl))
                        .GET()
                        .build();
                HttpResponse<String> resp = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

                if (resp.statusCode() != 200) {
                    return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                            .body(Map.of("error", "Invalid Google access token"));
                }

                JsonNode tokenInfo = objectMapper.readTree(resp.body());
                String tokenEmail = tokenInfo.has("email") ? tokenInfo.get("email").asText() : "";
                if (!email.equalsIgnoreCase(tokenEmail)) {
                    return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                            .body(Map.of("error", "Email mismatch"));
                }
            } else {
                // Treat as ID token — verify with Google
                String tokenInfoUrl = "https://oauth2.googleapis.com/tokeninfo?id_token=" + credential;
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(tokenInfoUrl))
                        .GET()
                        .build();
                HttpResponse<String> resp = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

                if (resp.statusCode() != 200) {
                    return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                            .body(Map.of("error", "Invalid Google token"));
                }

                JsonNode tokenInfo = objectMapper.readTree(resp.body());
                email = tokenInfo.has("email") ? tokenInfo.get("email").asText() : null;
                if (tokenInfo.has("given_name")) givenName = tokenInfo.get("given_name").asText();
                if (tokenInfo.has("family_name")) familyName = tokenInfo.get("family_name").asText();
            }

            if (email == null || email.isBlank()) {
                return ResponseEntity.badRequest().body(Map.of("error", "Email not provided"));
            }

            // Find or create user by email
            Optional<User> userOpt = userRepository.findByEmail(email);
            User user;

            if (userOpt.isPresent()) {
                user = userOpt.get();
            } else {
                // Generate username from email prefix
                String baseUsername = email.split("@")[0].toLowerCase();
                String finalUsername = baseUsername;
                int counter = 1;
                while (userRepository.existsByUsername(finalUsername)) {
                    finalUsername = baseUsername + counter;
                    counter++;
                }

                user = User.builder()
                        .username(finalUsername)
                        .email(email)
                        .firstName(givenName)
                        .lastName(familyName)
                        .hasUsablePassword(false)
                        .build();
                userRepository.save(user);
            }

            AuthResponse response = AuthResponse.builder()
                    .access(jwtUtil.generateAccessToken(user.getUsername()))
                    .refresh(jwtUtil.generateRefreshToken(user.getUsername()))
                    .user(UserDto.from(user))
                    .build();

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Invalid Google token"));
        }
    }

    @PostMapping("/token/refresh")
    public ResponseEntity<?> refreshToken(@RequestBody Map<String, String> body) {
        String refreshToken = body.get("refresh");

        if (refreshToken == null || refreshToken.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Refresh token is required"));
        }

        if (!jwtUtil.validateToken(refreshToken)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Invalid or expired refresh token"));
        }

        String username = jwtUtil.extractUsername(refreshToken);
        // Blacklist old refresh token (rotate)
        jwtUtil.blacklistToken(refreshToken);

        return ResponseEntity.ok(Map.of(
                "access", jwtUtil.generateAccessToken(username),
                "refresh", jwtUtil.generateRefreshToken(username)
        ));
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout(@RequestBody Map<String, String> body) {
        String refreshToken = body.get("refresh");
        if (refreshToken != null) {
            try {
                jwtUtil.blacklistToken(refreshToken);
            } catch (Exception ignored) {}
        }
        return ResponseEntity.ok(Map.of("message", "Logged out successfully"));
    }

    @GetMapping("/me")
    public ResponseEntity<?> me(@AuthenticationPrincipal User user) {
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Not authenticated"));
        }
        return ResponseEntity.ok(UserDto.from(user));
    }
}
