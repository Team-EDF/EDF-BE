package com.edf.teamedf.domain.activity.command.application.controller;

import com.edf.teamedf.common.security.auth.AuthUtils;
import com.edf.teamedf.common.security.auth.UserPrincipal;
import com.edf.teamedf.domain.activity.command.application.dto.CertifyRequest;
import com.edf.teamedf.domain.activity.command.application.dto.CertifyResponse;
import com.edf.teamedf.domain.activity.command.application.dto.EcoActivityResponse;
import com.edf.teamedf.domain.activity.command.application.dto.EcoActivitySummaryResponse;
import com.edf.teamedf.domain.activity.command.application.service.EcoActivityService;
import com.edf.teamedf.domain.activity.command.domain.EcoCategory;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 친환경 활동 인증 API.
 *
 * <p>인증 판정은 카테고리 기반 고정 규칙이며 AI 판정과는 무관하다.</p>
 */
@RestController
@RequestMapping("/activities")
@RequiredArgsConstructor
public class EcoActivityController {

    private final EcoActivityService ecoActivityService;

    /** 활동 인증 (프론트 UploadScreen). */
    @PostMapping
    public ResponseEntity<CertifyResponse> certify(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestBody CertifyRequest request) {
        Long userId = AuthUtils.requireUserId(principal);
        return ResponseEntity.ok(ecoActivityService.certify(userId, request));
    }

    /** 내 인증 기록 목록. */
    @GetMapping("/me")
    public ResponseEntity<Page<EcoActivityResponse>> getMyActivities(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(required = false) String category,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        Long userId = AuthUtils.requireUserId(principal);
        return ResponseEntity.ok(ecoActivityService.getMyActivities(userId, category, pageable));
    }

    /** 내 활동 요약 (총 인증 수 / 절감량 / 포인트 / 레벨 / 연속일). */
    @GetMapping("/me/summary")
    public ResponseEntity<EcoActivitySummaryResponse> getMySummary(
            @AuthenticationPrincipal UserPrincipal principal) {
        Long userId = AuthUtils.requireUserId(principal);
        return ResponseEntity.ok(ecoActivityService.getMySummary(userId));
    }

    /** 최근 N일 일별 인증 추이. */
    @GetMapping("/me/trend")
    public ResponseEntity<List<EcoActivityService.DailyPoint>> getTrend(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(defaultValue = "7") int days) {
        Long userId = AuthUtils.requireUserId(principal);
        return ResponseEntity.ok(ecoActivityService.getDailyTrend(userId, days));
    }

    /** 선택 가능한 인증 카테고리 목록 (프론트 하드코딩 제거용). */
    @GetMapping("/categories")
    public ResponseEntity<List<Map<String, Object>>> getCategories() {
        List<Map<String, Object>> categories = java.util.Arrays.stream(EcoCategory.values())
                .map(c -> Map.<String, Object>of(
                        "key", c.name(),
                        "label", c.getDisplayName(),
                        "detectionName", c.getDetectionName(),
                        "savedCarbon", c.getSavedCarbon(),
                        "points", c.getPoints()))
                .toList();
        return ResponseEntity.ok(categories);
    }

    @DeleteMapping("/{activityId}")
    public ResponseEntity<Void> delete(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long activityId) {
        Long userId = AuthUtils.requireUserId(principal);
        ecoActivityService.delete(userId, activityId);
        return ResponseEntity.noContent().build();
    }
}
