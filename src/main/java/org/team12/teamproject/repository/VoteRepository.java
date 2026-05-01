package org.team12.teamproject.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.team12.teamproject.entity.Vote;

import java.util.List;
import java.util.Optional;

public interface VoteRepository extends JpaRepository<Vote, Long> {

    boolean existsByTargetTypeAndTargetIdAndUserId(
            String targetType,
            Long targetId,
            Long userId
    );

    Optional<Vote> findByTargetTypeAndTargetIdAndUserId(
            String targetType,
            Long targetId,
            Long userId
    );

    List<Vote> findByUser_IdOrderByCreatedAtDesc(Long userId);

    List<Vote> findByTargetTypeAndTargetIdOrderByCreatedAtDesc(
            String targetType,
            Long targetId
    );
}