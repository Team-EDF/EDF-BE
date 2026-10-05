package com.edf.teamedf.domain.community.command.application.controller;

import com.edf.teamedf.common.security.auth.AuthUtils;
import com.edf.teamedf.common.security.auth.UserPrincipal;
import com.edf.teamedf.domain.community.command.application.dto.report.ReportCreateRequest;
import com.edf.teamedf.domain.community.command.application.dto.report.ReportProcessRequest;
import com.edf.teamedf.domain.community.command.application.dto.report.ReportResponse;
import com.edf.teamedf.domain.community.command.application.service.ReportService;
import com.edf.teamedf.domain.community.command.domain.Report;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class ReportController {

    private final ReportService reportService;

    /** 게시글/댓글/사용자 신고 (로그인 필요). */
    @PostMapping("/reports")
    public ResponseEntity<ReportResponse> createReport(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody ReportCreateRequest request) {
        Long reporterId = AuthUtils.requireUserId(principal);
        return ResponseEntity.status(HttpStatus.CREATED).body(reportService.createReport(reporterId, request));
    }

    // ==================== 운영자 (SecurityConfig 에서 /admin/** 는 ADMIN 전용) ====================

    /** 신고 목록. status 미지정 시 전체 (PENDING | RESOLVED | REJECTED). */
    @GetMapping("/admin/reports")
    public ResponseEntity<Page<ReportResponse>> getReports(
            @RequestParam(required = false) Report.Status status,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(reportService.getReports(status, pageable));
    }

    /** 신고 처리 (상태 변경 + 필요 시 콘텐츠 숨김). */
    @PatchMapping("/admin/reports/{reportId}")
    public ResponseEntity<ReportResponse> processReport(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long reportId,
            @Valid @RequestBody ReportProcessRequest request) {
        Long adminId = AuthUtils.requireUserId(principal);
        return ResponseEntity.ok(reportService.processReport(adminId, reportId, request));
    }
}
