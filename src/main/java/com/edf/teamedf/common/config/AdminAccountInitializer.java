package com.edf.teamedf.common.config;

import com.edf.teamedf.domain.user.command.domain.User;
import com.edf.teamedf.domain.user.command.infrastructure.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 서버 기동 시 관리자 계정이 없으면 만들어 둔다 (신고 처리 등 /admin/** API 용).
 *
 * 계정 정보는 application.yml 의 app.admin.* (환경변수 ADMIN_EMAIL / ADMIN_PASSWORD / ADMIN_NAME) 에서 읽는다.
 * 값을 지정하지 않으면 예시 계정(admin@example.com)으로 만들어지므로, 운영 환경에서는 반드시 환경변수로 바꾼다.
 * 이미 같은 이메일의 계정이 있으면 건드리지 않는다. ADMIN_ENABLED=false 로 끌 수 있다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AdminAccountInitializer implements ApplicationRunner {

    static final String EXAMPLE_PASSWORD = "admin1234!";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.admin.enabled:true}")
    private boolean enabled;

    @Value("${app.admin.email:admin@example.com}")
    private String email;

    @Value("${app.admin.password:" + EXAMPLE_PASSWORD + "}")
    private String password;

    @Value("${app.admin.name:관리자}")
    private String name;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!enabled) {
            return;
        }
        var existing = userRepository.findByEmail(email);
        if (existing.isPresent()) {
            if (existing.get().getRole() != User.Role.ADMIN) {
                log.warn("관리자 이메일({})로 가입된 계정이 있지만 ADMIN 권한이 아닙니다. 자동으로 바꾸지 않습니다.", email);
            }
            return;
        }

        userRepository.save(User.builder()
                .email(email)
                .password(passwordEncoder.encode(password))
                .name(name)
                .nickname(uniqueNickname())
                .role(User.Role.ADMIN)
                .provider("local")
                .gender(User.Gender.NONE)
                .termsAgreed(true)
                .privacyAgreed(true)
                .marketingAgreed(false)
                .enabled(true)
                .build());

        log.info("관리자 계정을 생성했습니다: {}", email);
        if (EXAMPLE_PASSWORD.equals(password)) {
            log.warn("관리자 계정이 예시 비밀번호로 만들어졌습니다. 운영 환경에서는 ADMIN_PASSWORD 를 지정하세요.");
        }
    }

    /** 닉네임은 unique 라서 일반 회원이 이미 'admin' 을 쓰고 있으면 겹치지 않는 값을 쓴다. */
    private String uniqueNickname() {
        String nickname = "admin";
        int suffix = 1;
        while (userRepository.existsByNickname(nickname)) {
            nickname = "admin" + suffix++;
        }
        return nickname;
    }
}
