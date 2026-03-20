package com.fairshare.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "users")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false, length = 150)
    private String username;

    @Column(length = 254)
    private String email = "";

    @Column(name = "first_name", length = 150)
    private String firstName = "";

    @Column(name = "last_name", length = 150)
    private String lastName = "";

    @JsonIgnore
    @Column(length = 128)
    private String password;

    @Column(name = "has_usable_password")
    private boolean hasUsablePassword = true;
}
