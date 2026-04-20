package org.team12.teamproject.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.team12.teamproject.entity.Comment;

import java.util.List;
import java.util.Optional;

public interface CommentRepository extends JpaRepository<Comment, Long> {

    List<Comment> findByPostIdAndStatusOrderByCreatedAtAsc(Long postId, String status);

    List<Comment> findByPostIdOrderByCreatedAtAsc(Long postId);

    Optional<Comment> findByIdAndStatus(Long commentId, String status);

    List<Comment> findByUser_IdOrderByCreatedAtDesc(Long userId);
}
