package org.team12.teamproject.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.team12.teamproject.entity.CommentVote;

import java.util.Optional;

public interface CommentVoteRepository extends JpaRepository<CommentVote, Long> {

    @Query("""
            select case when count(cv) > 0 then true else false end
            from CommentVote cv
            where cv.comment.id = :commentId
              and cv.user.id = :userId
            """)
    boolean existsByCommentIdAndUserId(Long commentId, Long userId);

    Optional<CommentVote> findByCommentIdAndUserId(Long commentId, Long userId);
}
