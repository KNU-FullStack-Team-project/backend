package org.team12.teamproject.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.team12.teamproject.entity.Post;

import java.util.List;
import java.util.Optional;

public interface PostRepository extends JpaRepository<Post, Long> {

    // 최근 공지 3개
    List<Post> findTop3ByIsNoticeTrueAndStatusOrderByCreatedAtDesc(String status);

    // 공지 전체 목록 (공지탭용)
    List<Post> findByIsNoticeTrueAndStatusOrderByCreatedAtDesc(String status);

    // 종목 일반글 목록
    List<Post> findByStockIdAndStatusAndIsNoticeFalseOrderByCreatedAtDesc(Long stockId, String status);

    Optional<Post> findByIdAndStatus(Long postId, String status);

    List<Post> findByUser_IdOrderByCreatedAtDesc(Long userId);

    List<Post> findByBoardIdAndStatusOrderByCreatedAtDesc(Long boardId, String status);
}
