package org.team12.teamproject.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import org.team12.teamproject.entity.Post;

import java.util.List;
import java.util.Optional;

public interface PostRepository extends JpaRepository<Post, Long> {

    List<Post> findTop3ByIsNoticeTrueAndStatusOrderByCreatedAtDesc(String status);

    List<Post> findByIsNoticeTrueAndStatusOrderByCreatedAtDesc(String status);

    List<Post> findByStockIdAndStatusAndIsNoticeFalseOrderByCreatedAtDesc(Long stockId, String status);

    Optional<Post> findByIdAndStatus(Long postId, String status);

    Optional<Post> findById(Long postId);

    List<Post> findByUser_IdOrderByCreatedAtDesc(Long userId);

    List<Post> findByBoardIdAndStatusOrderByCreatedAtDesc(Long boardId, String status);

    long countByUser_IdAndStatus(Long userId, String status);

    @Query("""
        SELECT COALESCE(SUM(p.likeCount), 0)
        FROM Post p
        WHERE p.user.id = :userId
          AND p.status = :status
    """)
    long sumLikeCountByUserIdAndStatus(
            @Param("userId") Long userId,
            @Param("status") String status
    );

    @Query("""
        SELECT COUNT(p)
        FROM Post p
        WHERE p.user.id = :userId
          AND p.reportCount > 0
    """)
    long countReportedPostsByUserId(@Param("userId") Long userId);
}