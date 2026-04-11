package org.team12.teamproject.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.team12.teamproject.entity.PostAttachment;

import java.util.List;

public interface PostAttachmentRepository extends JpaRepository<PostAttachment, Long> {
    List<PostAttachment> findByPostIdOrderByCreatedAtAsc(Long postId);
    List<PostAttachment> findByIdIn(List<Long> ids);
}