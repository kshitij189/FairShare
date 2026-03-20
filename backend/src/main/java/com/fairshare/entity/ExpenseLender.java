package com.fairshare.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "expense_lenders")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ExpenseLender {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "expense_id", nullable = false)
    private Expense expense;

    @Column(nullable = false, length = 150)
    private String username;

    @Column(nullable = false)
    private int amount;
}
