package com.edf.teamedf.common.config;

import com.edf.teamedf.common.security.auth.CustomUserDetailsService;
import com.edf.teamedf.common.security.jwt.JwtAuthenticationFilter;
import com.edf.teamedf.common.security.jwt.JwtTokenProvider;
import com.edf.teamedf.common.security.oauth.CustomOidcUserService;
import com.edf.teamedf.common.security.oauth.OAuth2SuccessHandler;
import com.edf.teamedf.common.security.oauth.OAuth2UserService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

//    private final OAuth2SuccessHandler oAuth2SuccessHandler;
//    private final OAuth2UserService oAuth2UserService;
//    private final CustomOidcUserService customOidcUserService;
    private final JwtTokenProvider jwtTokenProvider;
    private final CustomUserDetailsService userDetailsService;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {

        http
                // ========================================================
                // 기본 설정
                // ========================================================
                .csrf(csrf -> csrf.disable())
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((req, res, e) -> {
                            res.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                            res.setContentType("application/json;charset=UTF-8");
                            res.getWriter().write("{\"message\":\"Unauthorized\"}");
                        })
                        .accessDeniedHandler((req, res, e) -> {
                            res.setStatus(HttpServletResponse.SC_FORBIDDEN);
                            res.setContentType("application/json;charset=UTF-8");
                            res.getWriter().write("{\"message\":\"Forbidden\"}");
                        }))

                // ========================================================
                // 인가 설정 — 아래에 명시한 경로만 공개, 나머지는 로그인 필요
                // ========================================================
                .authorizeHttpRequests(auth -> auth
                        // 에러 응답 포워딩 (막으면 404/400 등이 전부 401 로 바뀜)
                        .requestMatchers("/error").permitAll()

                        // 회원가입/로그인/토큰 재발급/본인인증/계정 찾기
                        .requestMatchers("/auth/**").permitAll()

                        // 헬스체크/모니터링 (외부 노출 차단은 Ingress 에서 처리)
                        .requestMatchers("/actuator/health/**", "/actuator/info", "/actuator/prometheus").permitAll()

                        // 업로드된 정적 파일
                        .requestMatchers(HttpMethod.GET, "/files/**").permitAll()

                        // 비로그인 열람 허용 (게시글/댓글/뉴스/인증 카테고리)
                        .requestMatchers(HttpMethod.GET, "/posts", "/posts/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/news", "/news/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/activities/categories").permitAll()

                        // 내 정보 조회/수정/탈퇴
                        .requestMatchers("/users/me").authenticated()
                        // 회원 목록/검색/단건 조회는 이메일·전화번호를 포함하므로 관리자 전용
                        .requestMatchers("/users/**").hasRole("ADMIN")

                        .anyRequest().authenticated())

                // ========================================================
                // OAuth2 설정
                // ========================================================
//                .oauth2Login(oauth2 -> oauth2
//                        .loginPage("/login")
//                        .userInfoEndpoint(u -> u
//                                .userService(oAuth2UserService)
//                                .oidcUserService(customOidcUserService))
//                        .successHandler(oAuth2SuccessHandler))

                // ========================================================
                // 일반 로그인 인증 Provider 등록
                // ========================================================
                .userDetailsService(userDetailsService);

        http.addFilterBefore(
                new JwtAuthenticationFilter(jwtTokenProvider),
                UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(List.of("http://localhost:5173"));
        config.setAllowedMethods(List.of("*"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
