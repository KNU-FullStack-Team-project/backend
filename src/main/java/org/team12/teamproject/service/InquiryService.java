package org.team12.teamproject.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.team12.teamproject.dto.InquiryCreateRequestDto;
import org.team12.teamproject.dto.InquiryResponseDto;
import org.team12.teamproject.entity.Inquiry;
import org.team12.teamproject.entity.InquiryAnswer;
import org.team12.teamproject.entity.NotificationType;
import org.team12.teamproject.entity.User;
import org.team12.teamproject.repository.InquiryAnswerRepository;
import org.team12.teamproject.repository.InquiryRepository;
import org.team12.teamproject.repository.UserRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class InquiryService {

    private final InquiryRepository inquiryRepository;
    private final InquiryAnswerRepository inquiryAnswerRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;

    @Transactional
    public Long createInquiry(InquiryCreateRequestDto requestDto, String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 사용자입니다."));

        Inquiry inquiry = Inquiry.builder()
                .user(user)
                .category(requestDto.getCategory())
                .title(requestDto.getTitle())
                .content(requestDto.getContent())
                .isReadByUser(true) // 작성 시점엔 읽은 상태
                .build();

        Long id = inquiryRepository.save(inquiry).getId();

        // 관리자들에게 실시간 알림 발송
        List<User> admins = userRepository.findByRole("ADMIN");
        for (User admin : admins) {
            notificationService.sendNotification(
                    admin,
                    "신규 문의 접수",
                    "[" + requestDto.getCategory() + "] 새 문의가 등록되었습니다.",
                    NotificationType.INQUIRY);
        }

        return id;
    }

    @Transactional(readOnly = true)
    public List<InquiryResponseDto> getMyInquiries(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 사용자입니다."));

        return inquiryRepository.findByUserIdOrderByCreatedAtDesc(user.getId()).stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<InquiryResponseDto> getAllInquiries(String adminEmail) {
        User admin = userRepository.findByEmail(adminEmail)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 사용자입니다."));

        if (!"ADMIN".equalsIgnoreCase(admin.getRole())) {
            throw new RuntimeException("권한이 없습니다.");
        }

        return inquiryRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
    }

    @Transactional
    public void replyInquiry(Long inquiryId, String answerContent, String adminEmail) {
        User admin = userRepository.findByEmail(adminEmail)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 사용자입니다."));

        if (!"ADMIN".equalsIgnoreCase(admin.getRole())) {
            throw new RuntimeException("권한이 없습니다.");
        }

        Inquiry inquiry = inquiryRepository.findById(inquiryId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 문의입니다."));

        inquiry.setStatus("ANSWERED");
        inquiry.setReadByUser(false); // 사용자에게 알림이 가도록 false 처리
        inquiry.setUpdatedAt(LocalDateTime.now());

        InquiryAnswer inquiryAnswer = inquiryAnswerRepository.findByInquiryId(inquiryId)
                .orElse(InquiryAnswer.builder()
                        .inquiry(inquiry)
                        .admin(admin)
                        .build());

        inquiryAnswer.setContent(answerContent);
        inquiryAnswerRepository.save(inquiryAnswer);

        // 작성자에게 실시간 알림 발송
        notificationService.sendNotification(
                inquiry.getUser(),
                "문의 답변 등록",
                "남기신 문의에 대한 답변이 등록되었습니다.",
                NotificationType.INQUIRY);
    }

    @Transactional
    public void markAsRead(Long inquiryId, String email) {
        Inquiry inquiry = inquiryRepository.findById(inquiryId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 문의입니다."));

        System.out.println("[Debug] MarkAsRead Request - InquiryID: " + inquiryId + ", Email: " + email);

        // 본인 문의인 경우에만 읽음 처리 (대소문자 무시)
        if (inquiry.getUser().getEmail().equalsIgnoreCase(email)) {
            inquiry.setReadByUser(true);
            inquiryRepository.save(inquiry); // 명시적 저장
            System.out.println("[Debug] Inquiry " + inquiryId + " marked as READ for user " + email);
        } else {
            System.out.println("[Debug] MarkAsRead FAILED: Email mismatch. Owner: " + inquiry.getUser().getEmail());
        }
    }

    @Transactional(readOnly = true)
    public long getUnreadCount(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 사용자입니다."));

        if ("ADMIN".equalsIgnoreCase(user.getRole())) {
            // 관리자: 답변 대기(OPEN) 상태인 모든 문의 개수
            return inquiryRepository.countByStatus("OPEN");
        } else {
            // 일반 유저: 본인 문의 중 답변이 왔는데 아직 안 읽은 개수
            return inquiryRepository.countByUserIdAndIsReadByUserFalse(user.getId());
        }
    }

    private InquiryResponseDto convertToDto(Inquiry inquiry) {
        InquiryAnswer answerObj = inquiryAnswerRepository.findByInquiryId(inquiry.getId()).orElse(null);

        System.out.println("[Debug] Converting Inquiry ID: " + inquiry.getId());
        System.out.println(
                "[Debug] Content length: " + (inquiry.getContent() != null ? inquiry.getContent().length() : "NULL"));
        System.out.println("[Debug] Answer exist: " + (answerObj != null));

        return InquiryResponseDto.builder()
                .inquiryId(inquiry.getId())
                .category(inquiry.getCategory())
                .title(inquiry.getTitle())
                .content(inquiry.getContent())
                .status(inquiry.getStatus())
                .isReadByUser(inquiry.isReadByUser()) // DTO에도 읽음 여부 포함
                .answer(answerObj != null ? answerObj.getContent() : null)
                .nickname(inquiry.getUser().getNickname())
                .answeredAt(answerObj != null ? answerObj.getCreatedAt() : null)
                .createdAt(inquiry.getCreatedAt())
                .build();
    }
}
