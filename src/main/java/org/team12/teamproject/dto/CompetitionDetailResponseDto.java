package org.team12.teamproject.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonProperty;

@Getter
@AllArgsConstructor
public class CompetitionDetailResponseDto {
    private Long competitionId;
    private String title;
    private String description;
    private String status;
    private LocalDateTime startAt;
    private LocalDateTime endAt;
    private BigDecimal initialSeedMoney;
    private Integer maxParticipants;
    private Integer participantCount;

    @JsonProperty("isPublic")
    private boolean isPublic;
}