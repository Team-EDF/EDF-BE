package com.edf.teamedf.common.security.oauth;

import com.edf.teamedf.domain.user.command.domain.User;
import com.edf.teamedf.common.security.auth.UserPrincipal;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.user.OAuth2User;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;

public record CustomOAuth2User(User user, Map<String, Object> attributes, boolean isNewUser) implements OAuth2User {

    public UserPrincipal toUserPrincipal() {
        return UserPrincipal.builder()
                .userId(user.getUserId())
                .uuid(java.util.UUID.fromString(user.getUuid())) // User엔티티는 uuid가 String임
                .email(user.getEmail())
                .name(user.getName())
                .role(user.getRole().name())
                .build();
    }

    @Override
    public Map<String, Object> getAttributes() {
        return attributes;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return Collections.singletonList(new SimpleGrantedAuthority("ROLE_" + user.getRole()));
    }

    @Override
    public String getName() {
        return user.getName();
    }
}
