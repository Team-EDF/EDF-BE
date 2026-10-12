package com.edf.teamedf.common.security.oauth;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;

import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 앱(Android Google Sign-In)이 보낸 Google ID 토큰을 검증한다.
 *
 * <p>Google tokeninfo 엔드포인트가 서명·만료를 확인해 주고, 이 클래스는 그 결과에서
 * <b>우리 앱에 발급된 토큰인지(aud)</b>, 발급자(iss), 이메일 인증 여부(email_verified)를 추가로 확인한다.
 * aud 를 보지 않으면 다른 앱에서 로그인하며 받은 토큰을 그대로 가져와 우리 서비스에 로그인할 수 있다.</p>
 *
 * <p>허용 aud 는 google.oauth.client-ids (쉼표 구분) 이다. Android 앱은 GoogleSignin.configure 의
 * webClientId 로 발급받으므로 <b>웹 클라이언트 ID</b> 를 넣어야 한다. 비어 있으면 Google 로그인을 거부한다.</p>
 */
@Slf4j
@Component
public class GoogleIdTokenVerifier {

    private static final String TOKENINFO_URL = "https://oauth2.googleapis.com/tokeninfo";
    private static final Set<String> ISSUERS = Set.of("accounts.google.com", "https://accounts.google.com");

    private final Set<String> allowedAudiences;
    private final RestClient restClient;

    @Autowired
    public GoogleIdTokenVerifier(@Value("${google.oauth.client-ids:}") String clientIds) {
        this(clientIds, RestClient.builder().requestFactory(timeoutFactory()));
    }

    /** 테스트에서 MockRestServiceServer 를 붙일 수 있도록 builder 를 받는다. */
    GoogleIdTokenVerifier(String clientIds, RestClient.Builder builder) {
        this.allowedAudiences = Arrays.stream((clientIds == null ? "" : clientIds).split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .collect(Collectors.toUnmodifiableSet());
        this.restClient = builder.build();
        if (allowedAudiences.isEmpty()) {
            log.warn("google.oauth.client-ids(GOOGLE_CLIENT_ID) 가 비어 있어 Google 로그인을 모두 거부합니다.");
        }
    }

    private static SimpleClientHttpRequestFactory timeoutFactory() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(3_000);
        factory.setReadTimeout(5_000);
        return factory;
    }

    /** 검증을 통과한 Google 사용자 정보. email 은 소문자로 정규화한다. */
    public record GoogleIdentity(String subject, String email, String name) {
    }

    public GoogleIdentity verify(String idToken) {
        if (allowedAudiences.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Google 로그인이 서버에 설정되지 않았습니다. 관리자에게 문의해 주세요.");
        }
        if (!StringUtils.hasText(idToken) || idToken.split("\\.").length != 3) {
            throw invalid("Google ID 토큰 형식이 아닙니다.");
        }

        Map<String, Object> claims;
        try {
            claims = restClient.get()
                    .uri(TOKENINFO_URL + "?id_token={token}", idToken)
                    .retrieve()
                    .body(new ParameterizedTypeReference<Map<String, Object>>() {});
        } catch (RestClientResponseException e) {
            // 만료·위조·형식 오류는 Google 이 4xx 로 돌려준다
            throw invalid("Google 로그인 정보가 만료되었거나 올바르지 않습니다. 다시 시도해 주세요.");
        } catch (RestClientException e) {
            log.warn("Google tokeninfo 호출 실패: {}", e.toString());
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "Google 로그인 확인 중 오류가 발생했습니다. 잠시 후 다시 시도해 주세요.");
        }
        if (claims == null) {
            throw invalid("Google 로그인 정보를 확인하지 못했습니다.");
        }

        String aud = str(claims.get("aud"));
        if (!allowedAudiences.contains(aud)) {
            log.warn("허용되지 않은 aud 의 Google ID 토큰 거부: aud={}", aud);
            throw invalid("이 앱에서 발급된 Google 로그인 정보가 아닙니다.");
        }
        if (!ISSUERS.contains(str(claims.get("iss")))) {
            throw invalid("Google 로그인 정보의 발급자가 올바르지 않습니다.");
        }
        String subject = str(claims.get("sub"));
        if (!StringUtils.hasText(subject)) {
            throw invalid("Google 계정 식별자를 확인하지 못했습니다.");
        }
        String email = str(claims.get("email"));
        if (!StringUtils.hasText(email)) {
            throw invalid("Google 계정의 이메일을 확인하지 못했습니다. 이메일 제공에 동의해 주세요.");
        }
        // tokeninfo 는 문자열 "true"/"false" 로 준다
        if (!"true".equalsIgnoreCase(str(claims.get("email_verified")))) {
            throw invalid("Google 계정의 이메일이 인증되지 않았습니다.");
        }
        String name = str(claims.get("name"));
        return new GoogleIdentity(subject, email.trim().toLowerCase(Locale.ROOT),
                StringUtils.hasText(name) ? name : null);
    }

    private static String str(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static ResponseStatusException invalid(String message) {
        return new ResponseStatusException(HttpStatus.UNAUTHORIZED, message);
    }
}
