package com.edf.teamedf.domain.user.query.repository;

import com.edf.teamedf.domain.user.query.dto.user.UserSearchCondition;
import com.edf.teamedf.domain.user.command.domain.User;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;

public final class UserSpecification {

    private UserSpecification() {}

    public static Specification<User> withCondition(UserSearchCondition condition) {
        return Specification
                .where(hasKeyword(condition.keyword()))
                .and(hasRole(condition.role()))
                .and(isEnabled(condition.enabled()));
    }

    private static Specification<User> hasKeyword(String keyword) {
        if (!StringUtils.hasText(keyword)) return null;
        return (root, query, cb) -> {
            String pattern = "%" + keyword.toLowerCase() + "%";
            return cb.or(
                    cb.like(cb.lower(root.get("name")), pattern),
                    cb.like(cb.lower(root.get("email")), pattern),
                    cb.like(root.get("phone"), "%" + keyword + "%")
            );
        };
    }

    private static Specification<User> hasRole(User.Role role) {
        if (role == null) return null;
        return (root, query, cb) -> cb.equal(root.get("role"), role);
    }

    private static Specification<User> isEnabled(Boolean enabled) {
        if (enabled == null) return null;
        return (root, query, cb) -> cb.equal(root.get("enabled"), enabled);
    }
}
