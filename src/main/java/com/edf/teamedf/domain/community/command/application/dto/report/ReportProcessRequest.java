package com.edf.teamedf.domain.community.command.application.dto.report;

import com.edf.teamedf.domain.community.command.domain.Report;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 운영자 신고 처리 요청.
 *
 * @param hideContent true 이고 status 가 RESOLVED 이면 신고된 게시글/댓글을 숨김(소프트 삭제) 처리한다.
 */
public record ReportProcessRequest(
        @NotNull(message = "처리 상태는 필수입니다.") Report.Status status,
        @Size(max = 500) String adminMemo,
        Boolean hideContent
) {}
