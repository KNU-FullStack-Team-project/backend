package org.team12.teamproject.service;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import org.team12.teamproject.dto.CommunityBadgeResponseDto;
import org.team12.teamproject.dto.CommunityUserProfileResponseDto;
import org.team12.teamproject.entity.User;
import org.team12.teamproject.repository.CommentRepository;
import org.team12.teamproject.repository.HoldingRepository;
import org.team12.teamproject.repository.OrderRepository;
import org.team12.teamproject.repository.PostRepository;
import org.team12.teamproject.repository.UserRepository;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CommunityProfileService {

    private final UserRepository userRepository;
    private final PostRepository postRepository;
    private final CommentRepository commentRepository;
    private final HoldingRepository holdingRepository;
    private final OrderRepository orderRepository;

    private static final String BADGE_BASE_PATH = "/assets/community-badges/";

    @Transactional(readOnly = true)
    public CommunityUserProfileResponseDto getCommunityUserProfile(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 사용자입니다."));

        long postCount = postRepository.countByUser_IdAndStatus(userId, "NORMAL");
        long commentCount = commentRepository.countByUser_IdAndStatus(userId, "NORMAL");
        long receivedLikeCount = postRepository.sumLikeCountByUserIdAndStatus(userId, "NORMAL");
        long reportCount = postRepository.countReportedPostsByUserId(userId);
        long orderCount = orderRepository.countCompletedOrdersByUserId(userId);

        long holdingCount = holdingRepository.countActiveHoldingsByUserId(userId);
        long profitHoldingCount = holdingRepository.countProfitHoldingsByUserId(userId);
        long highProfitHoldingCount = holdingRepository.countHighProfitHoldingsByUserId(userId);

        int activityScore = calculateActivityScore(postCount, commentCount, receivedLikeCount);
        int level = calculateLevel(activityScore);
        String levelName = getLevelName(level);

        List<CommunityBadgeResponseDto> badges = createBadges(
                user,
                holdingCount,
                profitHoldingCount,
                highProfitHoldingCount,
                receivedLikeCount,
                commentCount,
                orderCount
        );

        return CommunityUserProfileResponseDto.builder()
                .userId(user.getId())
                .email(user.getEmail())
                .nickname(user.getNickname())
                .profileImageUrl(user.getProfileImageUrl())
                .role(user.getRole())
                .status(user.getStatus())
                .createdAt(user.getCreatedAt())
                .postCount(postCount)
                .commentCount(commentCount)
                .receivedLikeCount(receivedLikeCount)
                .reportCount(reportCount)
                .orderCount(orderCount)
                .activityScore(activityScore)
                .communityLevel(level)
                .levelName(levelName)
                .levelImageUrl(BADGE_BASE_PATH + "level-" + level + ".png")
                .badges(badges)
                .build();
    }

    @Transactional(readOnly = true)
    public List<CommunityUserProfileResponseDto> getAdminCommunityUsers() {
        return userRepository.findAll()
                .stream()
                .map(user -> getCommunityUserProfile(user.getId()))
                .toList();
    }

    private int calculateActivityScore(long postCount, long commentCount, long receivedLikeCount) {
        return (int) ((postCount * 5) + (commentCount * 2) + (receivedLikeCount * 10));
    }

    private int calculateLevel(int score) {
        if (score >= 1800) return 10;
        if (score >= 1200) return 9;
        if (score >= 800) return 8;
        if (score >= 500) return 7;
        if (score >= 300) return 6;
        if (score >= 180) return 5;
        if (score >= 100) return 4;
        if (score >= 50) return 3;
        if (score >= 20) return 2;
        return 1;
    }

    private String getLevelName(int level) {
        return switch (level) {
            case 10 -> "커뮤니티 마스터";
            case 9 -> "투자고수";
            case 8 -> "핵심유저";
            case 7 -> "전문가";
            case 6 -> "숙련자";
            case 5 -> "인기유저";
            case 4 -> "열정유저";
            case 3 -> "활동유저";
            case 2 -> "입문자";
            default -> "초보";
        };
    }

    private List<CommunityBadgeResponseDto> createBadges(
            User user,
            long holdingCount,
            long profitHoldingCount,
            long highProfitHoldingCount,
            long receivedLikeCount,
            long commentCount,
            long orderCount
    ) {
        List<CommunityBadgeResponseDto> badges = new ArrayList<>();

        if ("ADMIN".equalsIgnoreCase(user.getRole())) {
            badges.add(badge(
                    "ADMIN",
                    "운영진",
                    "관리자 또는 운영진 계정입니다.",
                    "badge-admin.png"
            ));
        }

        if (holdingCount > 0) {
            badges.add(badge(
                    "HOLDING",
                    "보유중",
                    "1개 이상의 종목을 보유 중입니다.",
                    "badge-holding.png"
            ));
        }

        if (profitHoldingCount > 0) {
            badges.add(badge(
                    "PROFIT",
                    "수익중",
                    "수익 중인 보유 종목이 있습니다.",
                    "badge-profit.png"
            ));
        }

        if (highProfitHoldingCount > 0) {
            badges.add(badge(
                    "MASTER",
                    "투자고수",
                    "수익률 20% 이상인 보유 종목이 있습니다.",
                    "badge-master.png"
            ));
        }

        if (receivedLikeCount >= 50) {
            badges.add(badge(
                    "POPULAR",
                    "인기유저",
                    "받은 추천 수가 50개 이상입니다.",
                    "badge-popular.png"
            ));
        }

        if (commentCount >= 50) {
            badges.add(badge(
                    "COMMUNICATION",
                    "소통왕",
                    "댓글 수가 50개 이상입니다.",
                    "badge-communication.png"
            ));
        }

        if (orderCount >= 30) {
            badges.add(badge(
                    "TRADER",
                    "단타왕",
                    "완료된 주문 수가 30개 이상입니다.",
                    "badge-trader.png"
            ));
        }

        return badges;
    }

    private CommunityBadgeResponseDto badge(
            String code,
            String label,
            String description,
            String fileName
    ) {
        return CommunityBadgeResponseDto.builder()
                .code(code)
                .label(label)
                .description(description)
                .imageUrl(BADGE_BASE_PATH + fileName)
                .build();
    }
}