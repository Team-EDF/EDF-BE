package com.edf.teamedf.domain.community.command.application.dto.report;

import com.edf.teamedf.domain.community.command.domain.Report;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ReportCreateRequest(
        @NotNull(message = "신고 대상 유형은 필수입니다.") Report.TargetType targetType,
        @NotNull(message = "신고 대상은 필수입니다.") Long targetId,
        @NotNull(message = "신고 사유는 필수입니다.") Report.Reason reason,
        @Size(max = 500, message = "상세 내용은 500자 이하여야 합니다.") String detail,
        Long postId
) {}
