package org.team12.teamproject.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class CommunityBadgeResponseDto {

    private String code;
    private String label;
    private String description;
    private String imageUrl;
}