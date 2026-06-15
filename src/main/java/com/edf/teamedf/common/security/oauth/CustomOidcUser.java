package com.edf.teamedf.common.security.oauth;

import com.edf.teamedf.common.security.auth.UserPrincipal;
import com.edf.teamedf.domain.user.command.domain.User;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.OidcUserInfo;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class CustomOidcUser implements OidcUser {

    private final User user;
    private final OidcUser delegate;

    public CustomOidcUser(User user, OidcUser delegate) {
        this.user = user;
        this.delegate = delegate;
    }

    public UserPrincipal toUserPrincipal() {
        return UserPrincipal.builder()
                .userId(user.getUserId())
                .uuid(UUID.fromString(user.getUuid()))
                .email(user.getEmail())
                .name(user.getName())
                .role(user.getRole().name())
                .build();
    }

    @Override
    public Map<String, Object> getClaims() { return delegate.getClaims(); }

    @Override
    public OidcUserInfo getUserInfo() { return delegate.getUserInfo(); }

    @Override
    public OidcIdToken getIdToken() { return delegate.getIdToken(); }

    @Override
    public Map<String, Object> getAttributes() { return delegate.getAttributes(); }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole()));
    }

    @Override
    public String getName() { return user.getName(); }
}
