package com.edf.teamedf.domain.user.query.controller;

import com.edf.teamedf.domain.user.query.dto.user.UserSearchCondition;
import com.edf.teamedf.domain.user.query.dto.user.UserSummaryResponse;
import com.edf.teamedf.domain.user.command.application.service.UserQueryService;
import com.edf.teamedf.domain.user.command.domain.User;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
public class UserQueryController {

    private final UserQueryService userQueryService;

    // 회원 목록 조회 (페이징)
    // GET /users?page=0&size=20&sort=createdAt,desc
    @GetMapping
    public ResponseEntity<Page<UserSummaryResponse>> getUsers(
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(userQueryService.getUsers(pageable));
    }

    // 회원 검색 (keyword + role + enabled 필터, 페이징)
    // GET /users/search?keyword=홍&role=MEMBER&enabled=true&page=0&size=20
    @GetMapping("/search")
    public ResponseEntity<Page<UserSummaryResponse>> searchUsers(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) User.Role role,
            @RequestParam(required = false) Boolean enabled,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(
                userQueryService.searchUsers(new UserSearchCondition(keyword, role, enabled), pageable));
    }

    // 회원 단건 조회
    // GET /users/{userId}
    @GetMapping("/{userId}")
    public ResponseEntity<UserSummaryResponse> getUser(@PathVariable Long userId) {
        return ResponseEntity.ok(userQueryService.getUser(userId));
    }
}
