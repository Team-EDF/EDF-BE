package com.edf.teamedf.common.security.oauth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.edf.teamedf.domain.user.command.infrastructure.UserRepository;
import com.edf.teamedf.common.security.auth.RefreshTokenStore;
import com.edf.teamedf.common.security.auth.UserPrincipal;
import com.edf.teamedf.common.security.jwt.JwtTokenProvider;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class OAuth2SuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private final JwtTokenProvider jwtTokenProvider;
    private final RefreshTokenStore tokenStore;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) {

        try {
            UserPrincipal principal;
            if (authentication.getPrincipal() instanceof CustomOidcUser oidcUser) {
                principal = oidcUser.toUserPrincipal();
            } else {
                principal = ((CustomOAuth2User) authentication.getPrincipal()).toUserPrincipal();
            }

            String uuid = principal.uuid().toString();

            String email = principal.email() != null ? principal.email() : "";
            String name = principal.name() != null ? principal.name() : "Unknown";
            String role = principal.role() != null ? principal.role().toString() : "ROLE_USER"; // Role이 Enum일 경우
            // toString()

            // Access Token 생성
            String accessToken = jwtTokenProvider.createAccessToken(
                    uuid,
                    Map.of(
                            "email", email,
                            "name", name,
                            "role", role,
                            "userId", principal.userId()
                    ));

            String refreshToken = jwtTokenProvider.createRefreshToken(uuid);

            tokenStore.save(
                    uuid,
                    refreshToken,
                    jwtTokenProvider.getRefreshExpSeconds());


            ResponseCookie refreshCookie = ResponseCookie.from("refreshToken", refreshToken)
                    .httpOnly(true)
                    .secure(false) // localhost는 false
                    .sameSite("Lax")
                    .path("/")
                    .maxAge(jwtTokenProvider.getRefreshExpSeconds())
                    .build();

            response.addHeader(HttpHeaders.SET_COOKIE, refreshCookie.toString());

            String redirectUrl = "http://localhost:5173/oauth2/success"
                    + "?accessToken=" + URLEncoder.encode(accessToken, StandardCharsets.UTF_8);

            getRedirectStrategy().sendRedirect(request, response, redirectUrl);

        } catch (Exception e) {
            log.error("OAuth2 Success 처리 중 오류", e);
            try {

                if (!response.isCommitted()) {
                    response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "OAuth2 처리 실패");
                }
            } catch (Exception ignored) {
            }
        }
    }

}
