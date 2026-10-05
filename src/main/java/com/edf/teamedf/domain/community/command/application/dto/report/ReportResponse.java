package com.edf.teamedf.domain.community.command.application.dto.report;

import com.edf.teamedf.domain.community.command.domain.Report;

import java.time.LocalDateTime;

public record ReportResponse(
        Long reportId,
        Long reporterId,
        Report.TargetType targetType,
        Long targetId,
        Long targetUserId,
        Long postId,
        Report.Reason reason,
        String detail,
        Report.Status status,
        String adminMemo,
        LocalDateTime createdAt,
        LocalDateTime processedAt
) {

    public static ReportResponse from(Report report) {
        return new ReportResponse(
                report.getReportId(),
                report.getReporter() == null ? null : report.getReporter().getUserId(),
                report.getTargetType(),
                report.getTargetId(),
                report.getTargetUserId(),
                report.getPostId(),
                report.getReason(),
                report.getDetail(),
                report.getStatus(),
                report.getAdminMemo(),
                report.getCreatedAt(),
                report.getProcessedAt()
        );
    }
}
