package com.fairshare.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "debts", uniqueConstraints = @UniqueConstraint(columnNames = {"group_id", "from_user", "to_user"}))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Debt {

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
