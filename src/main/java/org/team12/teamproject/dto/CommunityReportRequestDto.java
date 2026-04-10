package org.team12.teamproject.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CommunityReportRequestDto {
    private String reason;
    private String detail;
}