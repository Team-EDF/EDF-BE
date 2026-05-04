package com.edf.teamedf.common.security.auth;

import com.edf.teamedf.domain.user.command.domain.User;
import lombok.Builder;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public record UserPrincipal(UUID uuid, String email, String name, String role, Long userId) implements UserDetails {

    @Builder
    public UserPrincipal {
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + this.role));
    }

    @Override
    public String getPassword() {
        return "";
    }

    @Override
    public String getUsername() {
        // username 역할 → 여기서는 uuid 사용
        return this.uuid.toString();
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

    public static UserPrincipal from(User user) {
        return new UserPrincipal(
                UUID.fromString(user.getUuid()),
                user.getEmail(),
                user.getName(),
                user.getRole().name(),
                user.getUserId());
    }
}
