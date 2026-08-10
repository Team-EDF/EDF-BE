package com.edf.teamedf.domain.user.command.application.service;

import com.edf.teamedf.common.security.oauth.*;
import com.edf.teamedf.domain.user.command.domain.User;
import com.edf.teamedf.domain.user.command.infrastructure.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;

import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AppOAuth2Service {

    private final UserRepository userRepository;
    private final AuthService authService;
    private final RestClient restClient = RestClient.create();

    @Transactional
    public AuthService.TokenPair loginOrSignUpFromApp(String provider, String token) {
        OAuth2UserInfo userInfo = fetchSocialUserInfo(provider, token);

        User user = userRepository.findByEmail(userInfo.getEmail())
                .orElseGet(() -> {
                    User newUser = User.builder()
                            .uuid(UUID.randomUUID().toString())
                            .email(userInfo.getEmail())
                            .name(userInfo.getName())
                            .role(User.Role.MEMBER)
                            .provider(userInfo.getProvider())
                            .providerId(userInfo.getProviderId())
                            .enabled(true)
                            .build();
                    return userRepository.save(newUser);
                });

        // 사용자가 이미 가입되어 있으나 소셜 연동 정보가 없는 경우 업데이트 처리 (선택)
        if (user.getProvider() == null || !user.getProvider().equalsIgnoreCase(provider)) {
            // 필요 시 provider 정보 업데이트
            // 여기서는 단순하게 기존 유저 정보를 유지하고 로그인 처리
        }

        return authService.issueTokensForUser(user);
    }

    private OAuth2UserInfo fetchSocialUserInfo(String provider, String token) {
        if ("kakao".equalsIgnoreCase(provider)) {
            Map<String, Object> attributes = restClient.get()
                    .uri("https://kapi.kakao.com/v2/user/me")
                    .header("Authorization", "Bearer " + token)
                    .retrieve()
                    .body(new ParameterizedTypeReference<Map<String, Object>>() {});
            return new KakaoUserInfo(attributes);
        } else if ("naver".equalsIgnoreCase(provider)) {
            Map<String, Object> attributes = restClient.get()
                    .uri("https://openapi.naver.com/v1/nid/me")
                    .header("Authorization", "Bearer " + token)
                    .retrieve()
                    .body(new ParameterizedTypeReference<Map<String, Object>>() {});
            return new NaverUserInfo(attributes);
        } else if ("google".equalsIgnoreCase(provider)) {
            Map<String, Object> attributes;
            // 구글은 주로 ID Token을 앱에서 전달하므로 JWT 포맷 검증 후 endpoint 분기
            if (token.contains(".") && token.split("\\.").length == 3) {
                // ID Token 검증 및 정보 파싱
                attributes = restClient.get()
                        .uri("https://oauth2.googleapis.com/tokeninfo?id_token=" + token)
                        .retrieve()
                        .body(new ParameterizedTypeReference<Map<String, Object>>() {});
            } else {
                // Access Token으로 유저 정보 획득
                attributes = restClient.get()
                        .uri("https://www.googleapis.com/oauth2/v3/userinfo")
                        .header("Authorization", "Bearer " + token)
                        .retrieve()
                        .body(new ParameterizedTypeReference<Map<String, Object>>() {});
            }
            return new GoogleUserInfo(attributes);
        } else {
            throw new IllegalArgumentException("지원하지 않는 소셜 로그인 공급자입니다: " + provider);
        }
    }
}
