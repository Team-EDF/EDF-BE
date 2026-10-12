package com.edf.teamedf.domain.image.command.domain;

public enum ReferenceType {
    CONSUMPTION_RECORD, // 소비 기록 첨부 이미지 (영수증 등)
    PROFILE,            // 사용자 프로필 이미지
    ECO_ACTIVITY        // 친환경 활동 사진 인증 (인증에 쓰이면 referenceId = eco_activities.activity_id)
}
