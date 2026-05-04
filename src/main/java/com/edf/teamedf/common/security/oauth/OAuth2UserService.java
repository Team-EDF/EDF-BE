package com.edf.teamedf.common.security.oauth;

import com.edf.teamedf.domain.user.command.domain.User;
import com.edf.teamedf.domain.user.command.infrastructure.UserRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class OAuth2UserService extends DefaultOAuth2UserService {

    private final UserRepository userRepository;

    @Override
    @Transactional
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        OAuth2User oAuth2User = super.loadUser(userRequest);

        String providerId = userRequest.getClientRegistration().getRegistrationId();

        OAuth2UserInfo userInfo;
        if (providerId.equalsIgnoreCase("google")) {
            userInfo = new GoogleUserInfo(oAuth2User.getAttributes());
        } else if (providerId.equalsIgnoreCase("naver")) {
            userInfo = new NaverUserInfo(oAuth2User.getAttributes());
        } else if (providerId.equalsIgnoreCase("kakao")) {
            userInfo = new KakaoUserInfo(oAuth2User.getAttributes());
        } else {
            throw new OAuth2AuthenticationException("Unsupported provider: " + providerId);
        }

        User user = userRepository.findByEmail(userInfo.getEmail()).orElse(null);
        boolean isNewUser = false;

        if (user == null) {
            user = User.builder()
                    .uuid(java.util.UUID.randomUUID().toString())
                    .email(userInfo.getEmail())
                    .name(userInfo.getName())
                    .role(User.Role.MEMBER)
                    .provider(userInfo.getProvider())
                    .providerId(userInfo.getProviderId())
                    .build();
            userRepository.save(user);
            isNewUser = true;

            return new CustomOAuth2User(user, oAuth2User.getAttributes(), isNewUser);
        }

        // 이미 기존 유저가 있지만 소셜 정보가 없다면 업데이트할 수도 있습니다 (필요에 따라).
        // 여기서는 기존에 가입된 소셜 정보와 다를 수 있는 부분은 제외하고 바로 리턴합니다.

        return new CustomOAuth2User(user, oAuth2User.getAttributes(), isNewUser);
    }
}
