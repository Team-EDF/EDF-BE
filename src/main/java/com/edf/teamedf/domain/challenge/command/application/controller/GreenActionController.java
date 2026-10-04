package com.edf.teamedf.domain.challenge.command.application.controller;

import com.edf.teamedf.common.security.auth.AuthUtils;
import com.edf.teamedf.common.security.auth.UserPrincipal;
import com.edf.teamedf.domain.challenge.command.application.dto.CheckInResponse;
import com.edf.teamedf.domain.challenge.command.application.dto.UserChallengeResponse;
import com.edf.teamedf.domain.challenge.command.application.service.ChallengeService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

/**
 * Green Action API (시제품): 설문 -> Green Profile -> 이번 주 챌린지 -> 체크/완료.
 */
@RestController
@RequestMapping("/green")
@RequiredArgsConstructor
public class GreenActionController {

    private final ChallengeService challengeService;

    /** 설문 제출. 답변을 저장하고 AI가 계산한 Green Profile을 돌려준다 (다시 제출하면 덮어씀). */
    @PostMapping("/survey")
    public ResponseEntity<Map<String, Object>> submitSurvey(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestBody Map<String, Object> answers) {
        Long userId = AuthUtils.requireUserId(principal);
        return ResponseEntity.ok(challengeService.submitSurvey(userId, answers));
    }

    /** 내 Green Profile (설문 전이면 404). */
    @GetMapping("/profile")
    public ResponseEntity<Map<String, Object>> getProfile(@AuthenticationPrincipal UserPrincipal principal) {
        Long userId = AuthUtils.requireUserId(principal);
        return ResponseEntity.ok(challengeService.getMyProfile(userId));
    }

    /** 이번 주 챌린지 3개 (없으면 AI 추천으로 부여, 설문 전이면 409). */
    @GetMapping("/challenges")
    public ResponseEntity<List<UserChallengeResponse>> getChallenges(
            @AuthenticationPrincipal UserPrincipal principal) {
        Long userId = AuthUtils.requireUserId(principal);
        return ResponseEntity.ok(challengeService.getThisWeekChallenges(userId));
    }

    /**
     * 이 컨트롤러의 오류 응답에 사용자에게 보여 줄 안내 문구(message)를 담는다.
     * (Spring 기본 설정은 ResponseStatusException의 사유를 응답에 싣지 않아서
     *  "오늘은 이미 체크했어요" 같은 문구가 앱까지 전달되지 않는다. 전역 설정은 건드리지 않는다.)
     */
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> handleStatus(ResponseStatusException e) {
        String message = e.getReason() == null ? "요청을 처리하지 못했어요." : e.getReason();
        return ResponseEntity.status(e.getStatusCode()).body(Map.of("message", message));
    }

    /** 자율 체크 (하루 1회). 목표를 채우면 완료되고 포인트가 지급된다. */
    @PostMapping("/challenges/{userChallengeId}/check-in")
    public ResponseEntity<CheckInResponse> checkIn(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long userChallengeId) {
        Long userId = AuthUtils.requireUserId(principal);
        return ResponseEntity.ok(challengeService.checkIn(userId, userChallengeId));
    }
}
