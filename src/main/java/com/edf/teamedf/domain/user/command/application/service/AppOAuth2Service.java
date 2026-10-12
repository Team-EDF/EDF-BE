package com.edf.teamedf.domain.user.command.application.service;

import com.edf.teamedf.common.security.oauth.GoogleIdTokenVerifier;
import com.edf.teamedf.common.security.oauth.GoogleIdTokenVerifier.GoogleIdentity;
import com.edf.teamedf.domain.user.command.domain.User;
import com.edf.teamedf.domain.user.command.infrastructure.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

/**
 * 앱 소셜 로그인. 현재 지원 공급자는 Google 하나다 (카카오·네이버는 사용하지 않는다).
 *
 * <p>계정 매칭 순서</p>
 * <ol>
 *   <li>provider=google + providerId(Google sub) 로 찾는다. 이메일이 바뀌어도 같은 계정으로 들어온다.</li>
 *   <li>없으면 이메일(대소문자 무시)로 찾는다. Google 이 email_verified=true 로 확인한 이메일이고,
 *       우리 이메일 가입도 인증번호로 이메일을 확인하므로 같은 사람으로 본다.
 *       단 그 계정이 <b>다른 Google 계정(sub)</b> 에 연결돼 있으면 거부한다.</li>
 *   <li>둘 다 없으면 필수 약관 동의를 확인한 뒤 새로 만든다.</li>
 * </ol>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AppOAuth2Service {

    static final String GOOGLE = "google";

    private final UserRepository userRepository;
    private final AuthService authService;
    private final GoogleIdTokenVerifier googleIdTokenVerifier;

    @Transactional
    public AuthService.TokenPair loginOrSignUpFromApp(String provider, String token, boolean consentGiven) {
        if (!GOOGLE.equalsIgnoreCase(provider == null ? "" : provider.trim())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "지원하지 않는 소셜 로그인 공급자입니다: " + provider);
        }

        GoogleIdentity identity = googleIdTokenVerifier.verify(token);

        User user = userRepository.findByProviderAndProviderId(GOOGLE, identity.subject())
                .orElseGet(() -> findByVerifiedEmail(identity)
                        .orElseGet(() -> createGoogleUser(identity, consentGiven)));

        if (user.isWithdrawn() || Boolean.FALSE.equals(user.getEnabled())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "사용할 수 없는 계정입니다.");
        }

        return authService.issueTokensForUser(user);
    }

    private java.util.Optional<User> findByVerifiedEmail(GoogleIdentity identity) {
        List<User> matches = userRepository.findAllByEmailIgnoreCase(identity.email());
        if (matches.isEmpty()) {
            return java.util.Optional.empty();
        }
        if (matches.size() > 1) {
            log.warn("같은 이메일로 가입된 계정이 {}개라 Google 로그인을 연결하지 않습니다: {}", matches.size(), identity.email());
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "같은 이메일로 가입된 계정이 여러 개입니다. 고객센터로 문의해 주세요.");
        }
        User user = matches.get(0);
        if (GOOGLE.equalsIgnoreCase(user.getProvider())
                && user.getProviderId() != null
                && !user.getProviderId().equals(identity.subject())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "이 이메일은 다른 Google 계정과 연결되어 있습니다.");
        }
        return java.util.Optional.of(user);
    }

    private User createGoogleUser(GoogleIdentity identity, boolean consentGiven) {
        // 신규 소셜 가입은 필수 약관·개인정보 동의 및 만 14세 이상 확인 후에만 계정을 만든다.
        // 앱(LoginScreen)은 메시지의 CONSENT_REQUIRED 를 보고 동의 화면을 다시 띄운다.
        if (!consentGiven) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "CONSENT_REQUIRED: 필수 약관 동의 및 만 14세 이상 확인이 필요합니다.");
        }
        return userRepository.save(User.builder()
                .uuid(UUID.randomUUID().toString())
                .email(identity.email())
                .name(identity.name())
                .role(User.Role.MEMBER)
                .provider(GOOGLE)
                .providerId(identity.subject())
                .termsAgreed(true)
                .privacyAgreed(true)
                .marketingAgreed(false)
                .enabled(true)
                .build());
    }
}
