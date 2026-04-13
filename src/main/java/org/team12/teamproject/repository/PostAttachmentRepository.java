package org.team12.teamproject.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.team12.teamproject.entity.PostAttachment;

import java.util.List;

public interface PostAttachmentRepository extends JpaRepository<PostAttachment, Long> {
    List<PostAttachment> findByPostIdOrderByCreatedAtAsc(Long postId);
    List<PostAttachment> findByIdIn(List<Long> ids);

    @Query("SELECT COUNT(pa) FROM PostAttachment pa WHERE pa.post.id = :postId")
    int countByPostId(Long postId);

    @Query("SELECT pa FROM PostAttachment pa WHERE pa.post IS NULL AND pa.user.id = :userId ORDER BY pa.createdAt ASC")
    List<PostAttachment> findTempFilesByUser(Long userId);
}