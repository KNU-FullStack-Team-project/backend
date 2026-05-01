package org.team12.teamproject.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.team12.teamproject.entity.Notification;
import org.team12.teamproject.entity.NotificationType;
import org.team12.teamproject.entity.User;
import org.team12.teamproject.repository.NotificationRepository;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final SseService sseService;

    @Transactional
    public void sendNotification(User user, String title, String message, NotificationType type) {
        // 1. DB 저장
        Notification notification = Notification.builder()
                .user(user)
                .title(title)
                .message(message)
                .type(type)
                .isRead(0)
                .build();
        notificationRepository.save(notification);

        // 2. 실시간 SSE 전송
        sseService.sendNotification(user.getId(), notification);

        log.info("알림 생성 및 전송 완료: UserId={}, Type={}, Title={}, Message={}",
                user.getId(), type, title, message);
    }

    @Transactional(readOnly = true)
    public List<Notification> getNotifications(Long userId) {
        return notificationRepository.findByUserIdOrderByCreatedAtDesc(userId);
    }

    @Transactional(readOnly = true)
    public Long getUnreadCount(Long userId) {
        return notificationRepository.countByUserIdAndIsRead(userId, 0);
    }

    @Transactional
    public void markAsRead(Long notificationId) {
        notificationRepository.findById(notificationId)
                .ifPresent(Notification::markAsRead);
    }

    @Transactional
    public void markAllAsRead(Long userId) {
        notificationRepository.findByUserIdAndIsRead(userId, 0)
                .forEach(Notification::markAsRead);
    }
}
