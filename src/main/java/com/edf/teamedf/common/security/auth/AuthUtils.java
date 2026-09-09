package com.edf.teamedf.common.security.auth;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/**
 * SecurityConfig 가 현재 모든 요청을 permitAll 로 열어두고 있어서
 * 인증이 필요한 API 는 컨트롤러에서 principal 존재 여부를 직접 확인해야 한다.
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
