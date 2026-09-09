package com.edf.teamedf.domain.notification.command.application.controller;

import com.edf.teamedf.common.security.auth.UserPrincipal;
import com.edf.teamedf.domain.notification.command.application.dto.NotificationResponse;
import com.edf.teamedf.domain.notification.command.application.dto.UnreadCountResponse;
import com.edf.teamedf.domain.notification.command.application.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

@RestController
@RequestMapping("/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    /** 알림 목록 (페이징). unreadOnly=true 이면 미읽음만 */
    @GetMapping
    public ResponseEntity<Page<NotificationResponse>> getNotifications(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(defaultValue = "false") boolean unreadOnly,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(
                notificationService.getNotifications(requireUserId(principal), unreadOnly, pageable));
    }

    /** 미읽음 개수 (헤더 뱃지용) */
    @GetMapping("/unread-count")
    public ResponseEntity<UnreadCountResponse> getUnreadCount(
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(
                new UnreadCountResponse(notificationService.getUnreadCount(requireUserId(principal))));
    }

    /** 단건 읽음 처리 */
    @PatchMapping("/{notificationId}/read")
    public ResponseEntity<NotificationResponse> markAsRead(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long notificationId) {
        return ResponseEntity.ok(
                notificationService.markAsRead(requireUserId(principal), notificationId));
    }

    /** 전체 읽음 처리 */
    @PatchMapping("/read-all")
    public ResponseEntity<Map<String, Integer>> markAllAsRead(
            @AuthenticationPrincipal UserPrincipal principal) {
        int updated = notificationService.markAllAsRead(requireUserId(principal));
        return ResponseEntity.ok(Map.of("updated", updated));
    }

    @DeleteMapping("/{notificationId}")
    public ResponseEntity<Void> delete(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long notificationId) {
        notificationService.delete(requireUserId(principal), notificationId);
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }

    @DeleteMapping
    public ResponseEntity<Void> deleteAll(@AuthenticationPrincipal UserPrincipal principal) {
        notificationService.deleteAll(requireUserId(principal));
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }

    private Long requireUserId(UserPrincipal principal) {
        if (principal == null || principal.userId() == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다.");
        }
        return principal.userId();
    }
}
