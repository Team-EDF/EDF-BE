package com.edf.teamedf.domain.notification.command.application.dto;

import com.edf.teamedf.domain.notification.command.domain.Notification;

import java.time.LocalDateTime;

public record NotificationResponse(
        Long notificationId,
        String type,
        String iconKey,
        String typeLabel,
        String title,
        String message,
        String actorName,
        String linkType,
        Long linkId,
        boolean read,
        LocalDateTime createdAt
) {
    public static NotificationResponse from(Notification n) {
        return new NotificationResponse(
                n.getNotificationId(),
                n.getType().name(),
                n.getType().getIconKey(),
                n.getType().getLabel(),
                n.getTitle(),
                n.getMessage(),
                n.getActorName(),
                n.getLinkType(),
                n.getLinkId(),
                n.isRead(),
                n.getCreatedAt()
        );
    }
}
