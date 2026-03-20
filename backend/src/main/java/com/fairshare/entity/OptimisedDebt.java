package com.fairshare.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "optimised_debts")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class OptimisedDebt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "group_id", nullable = false)
    private ExpenseGroup group;

    @Column(name = "from_user", nullable = false, length = 150)
    private String fromUser;

    @Column(name = "to_user", nullable = false, length = 150)
    private String toUser;

    @Column(nullable = false)
    private int amount;
}
