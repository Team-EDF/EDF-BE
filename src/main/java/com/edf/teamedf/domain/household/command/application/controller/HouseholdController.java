package com.edf.teamedf.domain.household.command.application.controller;

import com.edf.teamedf.common.security.auth.AuthUtils;
import com.edf.teamedf.common.security.auth.UserPrincipal;
import com.edf.teamedf.domain.household.command.application.dto.BillReadResponse;
import com.edf.teamedf.domain.household.command.application.dto.HouseholdBillResponse;
import com.edf.teamedf.domain.household.command.application.dto.SaveBillRequest;
import com.edf.teamedf.domain.household.command.application.service.HouseholdService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

/**
 * 가정 에너지(관리비) API (시제품): 직접 입력 + 고지서 사진 읽기 하이브리드.
 */
@RestController
@RequestMapping("/household")
@RequiredArgsConstructor
public class HouseholdController {

    private final HouseholdService householdService;

    /**
     * 고지서 사진(1~3장, {@code images})을 AI로 읽어 사용월과 전기·수도·가스·난방 값을 돌려준다 (저장하지 않음, 사진도 저장 안 함).
     * 읽은 값은 화면에서 확인·수정한 뒤 {@code POST /household/bills}로 저장한다.
     */
    @PostMapping(value = "/bills/read", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<BillReadResponse> read(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam("images") List<MultipartFile> images) {
        Long userId = AuthUtils.requireUserId(principal);
        return ResponseEntity.ok(householdService.read(userId, images));
    }

    /** 한 달 관리비 저장(덮어쓰기). 탄소 계산과 작년 같은 달 대비 절감 포인트 지급까지 한다. */
    @PostMapping("/bills")
    public ResponseEntity<HouseholdBillResponse> save(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestBody SaveBillRequest request) {
        Long userId = AuthUtils.requireUserId(principal);
        return ResponseEntity.ok(householdService.save(userId, request));
    }

    /** 내 관리비 기록 (최근 달부터). */
    @GetMapping("/bills")
    public ResponseEntity<List<HouseholdBillResponse>> list(@AuthenticationPrincipal UserPrincipal principal) {
        Long userId = AuthUtils.requireUserId(principal);
        return ResponseEntity.ok(householdService.list(userId));
    }

    /** 오류 응답에 사용자에게 보여 줄 안내 문구(message)를 담는다 (GreenActionController와 같은 방식). */
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> handleStatus(ResponseStatusException e) {
        String message = e.getReason() == null ? "요청을 처리하지 못했어요." : e.getReason();
        return ResponseEntity.status(e.getStatusCode()).body(Map.of("message", message));
    }
}
