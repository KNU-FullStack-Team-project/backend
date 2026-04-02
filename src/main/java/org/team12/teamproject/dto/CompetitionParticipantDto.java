package org.team12.teamproject.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@AllArgsConstructor
public class CompetitionParticipantDto {

    private Long userId;
    private String email;
    private String nickname;
    private Long accountId;
    private LocalDateTime joinedAt;
    private String status;
}