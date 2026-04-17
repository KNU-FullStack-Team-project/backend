package org.team12.teamproject.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.team12.teamproject.entity.Inquiry;

import java.util.List;

public interface InquiryRepository extends JpaRepository<Inquiry, Long> {
    List<Inquiry> findByUserIdOrderByCreatedAtDesc(Long userId);
    List<Inquiry> findAllByOrderByCreatedAtDesc();

    // 사용자용: 읽지 않은 답변 개수
    long countByUserIdAndIsReadByUserFalse(Long userId);

    // 관리자용: 답변 대기(OPEN) 중인 문의 개수
    long countByStatus(String status);
}
