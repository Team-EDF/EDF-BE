package com.edf.teamedf.domain.community.command.application.controller;

import com.edf.teamedf.common.security.auth.AuthUtils;
import com.edf.teamedf.common.security.auth.UserPrincipal;
import com.edf.teamedf.domain.community.command.application.dto.block.BlockedUserResponse;
import com.edf.teamedf.domain.community.command.application.service.UserBlockService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 사용자 차단. /users/** 는 관리자 전용 경로라 별도의 /blocks 아래에 둔다.
 */
@RestController
@RequestMapping("/blocks")
@RequiredArgsConstructor
public class UserBlockController {

    private final UserBlockService userBlockService;

    /** 내가 차단한 사용자 목록. */
    @GetMapping
    public ResponseEntity<List<BlockedUserResponse>> getBlockedUsers(
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(userBlockService.getBlockedUsers(AuthUtils.requireUserId(principal)));
    }

    @PostMapping("/{userId}")
    public ResponseEntity<Void> block(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long userId) {
        userBlockService.block(AuthUtils.requireUserId(principal), userId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{userId}")
    public ResponseEntity<Void> unblock(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long userId) {
        userBlockService.unblock(AuthUtils.requireUserId(principal), userId);
        return ResponseEntity.noContent().build();
    }
}
