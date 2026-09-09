package com.edf.teamedf.domain.dashboard.command.application.controller;

import com.edf.teamedf.common.security.auth.AuthUtils;
import com.edf.teamedf.common.security.auth.UserPrincipal;
import com.edf.teamedf.domain.activity.command.application.dto.CertifyRequest;
import com.edf.teamedf.domain.activity.command.application.dto.CertifyResponse;
import com.edf.teamedf.domain.activity.command.application.service.EcoActivityService;
import com.edf.teamedf.domain.dashboard.command.application.dto.CarbonScoreResponse;
import com.edf.teamedf.domain.dashboard.command.application.dto.CategoryCarbonRatioResponse;
import com.edf.teamedf.domain.dashboard.command.application.dto.MonthlySpendingResponse;
import com.edf.teamedf.domain.dashboard.command.application.dto.WeeklyCarbonResponse;
import com.edf.teamedf.domain.dashboard.command.application.service.DashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;
    private final EcoActivityService ecoActivityService;

    // 탄소 절감 점수 조회 (이번달 기준)
    @GetMapping("/carbon-score")
    public ResponseEntity<CarbonScoreResponse> getCarbonScore(
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(dashboardService.getCarbonScore(AuthUtils.requireUserId(principal)));
    }

    // 카테고리별 탄소 배출 비율 조회 (이번달 기준)
    @GetMapping("/category-carbon")
    public ResponseEntity<List<CategoryCarbonRatioResponse>> getCategoryCarbonRatios(
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(dashboardService.getCategoryCarbonRatios(AuthUtils.requireUserId(principal)));
    }

    // 이번달 소비 금액 조회
    @GetMapping("/monthly-spending")
    public ResponseEntity<MonthlySpendingResponse> getMonthlySpending(
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(dashboardService.getMonthlySpending(AuthUtils.requireUserId(principal)));
    }

    // 주간 탄소 배출량 그래프 조회 (오늘 포함 최근 7일)
    @GetMapping("/weekly-carbon")
    public ResponseEntity<List<WeeklyCarbonResponse>> getWeeklyCarbonGraph(
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(dashboardService.getWeeklyCarbonGraph(AuthUtils.requireUserId(principal)));
    }

    /**
     * 친환경 활동 인증.
     *
     * <p>프론트엔드(api/upload.js)가 이미 이 경로를 호출하고 있어 호환을 위해 유지한다.
     * 실제 처리는 activity 도메인에 위임한다.</p>
     */
    @PostMapping("/certify")
    public ResponseEntity<CertifyResponse> certify(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestBody CertifyRequest request) {
        Long userId = AuthUtils.requireUserId(principal);
        return ResponseEntity.ok(ecoActivityService.certify(userId, request));
    }
}
