package org.team12.teamproject.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.team12.teamproject.entity.PostLike;

import java.util.List;

public interface PostLikeRepository extends JpaRepository<PostLike, Long> {

    @Query("""
            select case when count(pl) > 0 then true else false end
            from PostLike pl
            where pl.post.id = :postId
              and pl.user.id = :userId
            """)
    boolean existsByPostIdAndUserId(Long postId, Long userId);

    List<PostLike> findByUser_IdOrderByCreatedAtDesc(Long userId);
}
