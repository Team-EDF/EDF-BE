package com.edf.teamedf.domain.dashboard.command.application.controller;

import com.edf.teamedf.common.security.auth.AuthUtils;
import com.edf.teamedf.common.security.auth.UserPrincipal;
import com.edf.teamedf.domain.dashboard.command.application.RecordConfirmService;
import com.edf.teamedf.domain.dashboard.command.application.dto.ReceiptRecordResponse;
import com.edf.teamedf.domain.dashboard.command.application.service.ReceiptQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/records")
@RequiredArgsConstructor
public class RecordController {

    private final RecordConfirmService recordConfirmService;
    private final ReceiptQueryService receiptQueryService;

    // 영수증 캘린더용: 내가 등록한 영수증 목록 (최신순, 페이지 단위)
    @GetMapping("/me")
    public ResponseEntity<Page<ReceiptRecordResponse>> myReceipts(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return ResponseEntity.ok(receiptQueryService.getMyReceipts(AuthUtils.requireUserId(principal), page, size));
    }

    // /images/upload(referenceType=CONSUMPTION_RECORD) 응답의 referenceId가 recordId다.
    @PostMapping("/{recordId}/confirm")
    public ResponseEntity<Void> confirm(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long recordId) {
        recordConfirmService.confirmByRecordId(recordId, AuthUtils.requireUserId(principal));
        return ResponseEntity.ok().build();
    }
}
