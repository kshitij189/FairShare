package com.fairshare.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "user_debts", uniqueConstraints = @UniqueConstraint(columnNames = {"group_id", "username"}))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class UserDebt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "group_id", nullable = false)
    private ExpenseGroup group;

    @Column(nullable = false, length = 150)
    private String username;

    @Column(name = "net_debt")
    @Builder.Default
    private int netDebt = 0;
}
