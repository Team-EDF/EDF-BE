package com.edf.teamedf.domain.user.query.dto.user;

import com.edf.teamedf.domain.user.command.domain.User;

public record UserSearchCondition(
        String keyword,     // name / email / phone 부분 일치 검색
        User.Role role,     // 역할 필터 (null이면 전체)
        Boolean enabled     // 활성화 상태 필터 (null이면 전체)
) {}
