package com.edf.teamedf.domain.notification.command.domain;

import lombok.Getter;

/**
 * 알림 종류.
 * iconKey 는 클라이언트가 아이콘을 매핑할 때 사용하는 식별자이다.
 */
@Getter
public enum NotificationType {

    LIKE("like", "좋아요"),
    COMMENT("comment", "댓글"),
    REPLY("reply", "답글"),
    CERTIFY("certify", "활동 인증"),
    LEVEL_UP("levelUp", "레벨 업"),
    RANKING("ranking", "랭킹"),
    SYSTEM("system", "시스템");

    private final String iconKey;
    private final String label;

    NotificationType(String iconKey, String label) {
        this.iconKey = iconKey;
        this.label = label;
    }
}
