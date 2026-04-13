package org.team12.teamproject.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.team12.teamproject.dto.UserActivityItemDto;
import org.team12.teamproject.entity.Comment;
import org.team12.teamproject.entity.Order;
import org.team12.teamproject.entity.Post;
import org.team12.teamproject.entity.PostLike;
import org.team12.teamproject.repository.CommentRepository;
import org.team12.teamproject.repository.OrderRepository;
import org.team12.teamproject.repository.PostLikeRepository;
import org.team12.teamproject.repository.PostRepository;
import org.team12.teamproject.repository.UserRepository;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class UserActivityService {

    private final UserRepository userRepository;
    private final PostRepository postRepository;
    private final CommentRepository commentRepository;
    private final PostLikeRepository postLikeRepository;
    private final OrderRepository orderRepository;

    @Transactional(readOnly = true)
    public List<UserActivityItemDto> getUserActivities(Long userId) {
        userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("사용자를 찾을 수 없습니다."));

        List<UserActivityItemDto> activities = new ArrayList<>();

        for (Post post : postRepository.findByUser_IdOrderByCreatedAtDesc(userId)) {
            activities.add(UserActivityItemDto.builder()
                    .actionType("POST_CREATE")
                    .targetType("POST")
                    .targetTitle(post.getTitle())
                    .description("글 작성")
                    .occurredAt(post.getCreatedAt() != null ? post.getCreatedAt().toString() : null)
                    .build());

            if ("DELETED".equalsIgnoreCase(post.getStatus()) && post.getDeletedAt() != null) {
                activities.add(UserActivityItemDto.builder()
                        .actionType("POST_DELETE")
                        .targetType("POST")
                        .targetTitle(post.getTitle())
                        .description("글 삭제")
                        .occurredAt(post.getDeletedAt().toString())
                        .build());
            }
        }

        for (Comment comment : commentRepository.findByUser_IdOrderByCreatedAtDesc(userId)) {
            String commentTitle = comment.getPost() != null ? comment.getPost().getTitle() : null;

            activities.add(UserActivityItemDto.builder()
                    .actionType("COMMENT_CREATE")
                    .targetType("COMMENT")
                    .targetTitle(commentTitle)
                    .description("댓글 작성")
                    .occurredAt(comment.getCreatedAt() != null ? comment.getCreatedAt().toString() : null)
                    .build());

            if ("DELETED".equalsIgnoreCase(comment.getStatus()) && comment.getDeletedAt() != null) {
                activities.add(UserActivityItemDto.builder()
                        .actionType("COMMENT_DELETE")
                        .targetType("COMMENT")
                        .targetTitle(commentTitle)
                        .description("댓글 삭제")
                        .occurredAt(comment.getDeletedAt().toString())
                        .build());
            }
        }

        for (PostLike postLike : postLikeRepository.findByUser_IdOrderByCreatedAtDesc(userId)) {
            activities.add(UserActivityItemDto.builder()
                    .actionType("POST_LIKE")
                    .targetType("POST")
                    .targetTitle(postLike.getPost() != null ? postLike.getPost().getTitle() : null)
                    .description("글 추천")
                    .occurredAt(postLike.getCreatedAt() != null ? postLike.getCreatedAt().toString() : null)
                    .build());
        }

        for (Order order : orderRepository.findByAccount_User_IdOrderByOrderedAtDesc(userId)) {
            String stockName = order.getStock() != null ? order.getStock().getStockName() : null;
            String orderSide = "SELL".equalsIgnoreCase(order.getOrderSide()) ? "매도" : "매수";
            String quantityText = order.getQuantity() != null ? order.getQuantity() + "주" : "";

            activities.add(UserActivityItemDto.builder()
                    .actionType("ORDER_" + order.getOrderSide())
                    .targetType("ORDER")
                    .targetTitle(stockName)
                    .description(orderSide + " " + quantityText)
                    .occurredAt(order.getOrderedAt() != null ? order.getOrderedAt().toString() : null)
                    .build());
        }

        return activities.stream()
                .filter(activity -> activity.getOccurredAt() != null)
                .sorted(Comparator.comparing(UserActivityItemDto::getOccurredAt).reversed())
                .toList();
    }
}
