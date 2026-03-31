package org.team12.teamproject.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@AllArgsConstructor
public class CompetitionListResponseDto {

    private Long competitionId;
    private String title;
    private String description;
    private String status;
    private LocalDateTime startAt;
    private LocalDateTime endAt;
    private Integer participantCount;
}