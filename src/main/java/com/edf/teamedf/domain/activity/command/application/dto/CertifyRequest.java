package com.edf.teamedf.domain.activity.command.application.dto;

/**
 * 친환경 활동 인증 요청.
 *
 * <p>프론트엔드(api/upload.js)가 보내는 필드명을 그대로 받는다.
 * imageUri 는 이미 업로드된 이미지의 URL 또는 로컬 URI 문자열이다.</p>
 */
public record CertifyRequest(
        String imageUri,
        String category,
        String comment
) {
}
