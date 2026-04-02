package org.team12.teamproject.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
public class CompetitionSaveRequestDto {

    private String title;
    private String description;
    private LocalDateTime startAt;
    private LocalDateTime endAt;
    private BigDecimal initialSeedMoney;
    private Integer maxParticipants;
}