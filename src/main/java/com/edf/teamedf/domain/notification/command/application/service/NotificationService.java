package com.edf.teamedf.domain.notification.command.application.service;

import com.edf.teamedf.domain.notification.command.application.dto.NotificationResponse;
import com.edf.teamedf.domain.notification.command.domain.Notification;
import com.edf.teamedf.domain.notification.command.domain.NotificationType;
import com.edf.teamedf.domain.notification.command.infrastructure.NotificationRepository;
import com.edf.teamedf.domain.user.command.domain.User;
import com.edf.teamedf.domain.user.command.infrastructure.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;

    // ==================== 조회 ====================

    public Page<NotificationResponse> getNotifications(Long userId, boolean unreadOnly, Pageable pageable) {
        Page<Notification> page = unreadOnly
                ? notificationRepository.findAllByUser_UserIdAndIsReadFalseOrderByCreatedAtDesc(userId, pageable)
                : notificationRepository.findAllByUser_UserIdOrderByCreatedAtDesc(userId, pageable);
        return page.map(NotificationResponse::from);
    }

    public long getUnreadCount(Long userId) {
        return notificationRepository.countByUser_UserIdAndIsReadFalse(userId);
    }

    // ==================== 상태 변경 ====================

    @Transactional
    public NotificationResponse markAsRead(Long userId, Long notificationId) {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "알림을 찾을 수 없습니다."));
        validateOwner(notification, userId);
        notification.markAsRead();
        return NotificationResponse.from(notification);
    }

    @Transactional
    public int markAllAsRead(Long userId) {
        return notificationRepository.markAllAsRead(userId);
    }

    @Transactional
    public void delete(Long userId, Long notificationId) {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "알림을 찾을 수 없습니다."));
        validateOwner(notification, userId);
        notificationRepository.delete(notification);
    }

    @Transactional
    public void deleteAll(Long userId) {
        notificationRepository.deleteAllByUserId(userId);
    }

    // ==================== 발행 (다른 도메인에서 호출) ====================

    /**
     * 알림을 발행한다. 알림 실패가 본래 비즈니스 로직(좋아요, 댓글, 인증 등)을 롤백시키면 안 되므로
     * 예외는 로그만 남기고 삼킨다.
     */
    @Transactional
    public void notify(Long recipientUserId,
                       NotificationType type,
                       String title,
                       String message,
                       String linkType,
                       Long linkId,
                       String actorName) {
        try {
            if (recipientUserId == null) return;

            User recipient = userRepository.findById(recipientUserId).orElse(null);
            if (recipient == null) return;

            notificationRepository.save(Notification.builder()
                    .user(recipient)
                    .type(type)
                    .title(title)
                    .message(message)
                    .linkType(linkType)
                    .linkId(linkId)
                    .actorName(actorName)
                    .build());
        } catch (Exception e) {
            log.warn("알림 발행 실패 (userId={}, type={}): {}", recipientUserId, type, e.getMessage());
        }
    }

    /** 자기 자신에게는 알림을 보내지 않는다. */
    public void notifyOther(Long recipientUserId,
                            Long actorUserId,
                            NotificationType type,
                            String title,
                            String message,
                            String linkType,
                            Long linkId,
                            String actorName) {
        if (recipientUserId == null || recipientUserId.equals(actorUserId)) return;
        notify(recipientUserId, type, title, message, linkType, linkId, actorName);
    }

    private void validateOwner(Notification notification, Long userId) {
        if (!notification.getUser().getUserId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "본인의 알림만 처리할 수 있습니다.");
        }
    }
}
