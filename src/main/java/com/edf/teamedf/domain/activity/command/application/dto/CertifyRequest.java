package com.edf.teamedf.domain.activity.command.application.dto;

/**
 * 친환경 활동 인증 요청.
 *
 * <p>imageId: POST /images/upload?referenceType=ECO_ACTIVITY 로 먼저 올린 인증 사진의 ID (필수).
 * 서버는 본인이 최근에 올렸고 아직 인증에 쓰이지 않은 사진인지 확인한다.
 * imageUri 는 예전 앱 호환용으로만 남겨 두며 서버는 쓰지 않는다.</p>
 */
public record CertifyRequest(
        Long imageId,
        String imageUri,
        String category,
        String comment
) {
}
