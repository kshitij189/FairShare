package com.fairshare.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fairshare.entity.Debt;
import com.fairshare.entity.OptimisedDebt;
import lombok.*;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class DebtDto {
    private Long id;
    private Long group;

    @JsonProperty("from_user")
    private String fromUser;

    @JsonProperty("to_user")
    private String toUser;

    private int amount;

    public static DebtDto from(Debt debt) {
        return DebtDto.builder()
                .id(debt.getId())
                .group(debt.getGroup().getId())
                .fromUser(debt.getFromUser())
                .toUser(debt.getToUser())
                .amount(debt.getAmount())
                .build();
    }

    public static DebtDto from(OptimisedDebt debt) {
        return DebtDto.builder()
                .id(debt.getId())
                .group(debt.getGroup().getId())
                .fromUser(debt.getFromUser())
                .toUser(debt.getToUser())
                .amount(debt.getAmount())
                .build();
    }
}
