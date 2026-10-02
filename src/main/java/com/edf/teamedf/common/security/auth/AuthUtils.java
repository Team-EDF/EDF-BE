package com.edf.teamedf.common.security.auth;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/**
 * 인증 여부는 SecurityConfig 에서 먼저 막고, 컨트롤러에서는 principal 에서 userId 를 꺼낼 때 사용한다.
 * (SecurityConfig 에서 permitAll 로 열어둔 경로에서도 principal 이 null 일 때 500 대신 401 을 돌려준다)
 */
public final class AuthUtils {

    private AuthUtils() {}

    public static Long requireUserId(UserPrincipal principal) {
        if (principal == null || principal.userId() == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다.");
        }
        return principal.userId();
    }

    /** 비로그인도 허용하는 API 용. 로그인 상태가 아니면 null 을 반환한다. */
    public static Long optionalUserId(UserPrincipal principal) {
        return principal == null ? null : principal.userId();
    }
}
