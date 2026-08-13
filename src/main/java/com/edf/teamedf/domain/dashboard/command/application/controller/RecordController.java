package com.edf.teamedf.domain.dashboard.command.application.controller;

import com.edf.teamedf.common.security.auth.UserPrincipal;
import com.edf.teamedf.domain.dashboard.command.application.RecordConfirmService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/records")
@RequiredArgsConstructor
public class RecordController {

    private final RecordConfirmService recordConfirmService;

    // /images/upload(referenceType=CONSUMPTION_RECORD) 응답의 referenceId가 recordId다.
    @PostMapping("/{recordId}/confirm")
    public ResponseEntity<Void> confirm(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long recordId) {
        recordConfirmService.confirmByRecordId(recordId, principal.userId());
        return ResponseEntity.ok().build();
    }
}
