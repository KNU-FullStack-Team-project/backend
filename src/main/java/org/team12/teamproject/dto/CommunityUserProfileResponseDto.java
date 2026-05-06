package org.team12.teamproject.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Builder
public class CommunityUserProfileResponseDto {

    private Long userId;
    private String email;
    private String nickname;
    private String profileImageUrl;
    private String role;
    private String status;
    private LocalDateTime createdAt;

    private long postCount;
    private long commentCount;
    private long receivedLikeCount;
    private long reportCount;
    private long orderCount;

    private int activityScore;
    private int communityLevel;
    private String levelName;
    private String levelImageUrl;

    private List<CommunityBadgeResponseDto> badges;
}