package com.edf.teamedf.domain.user.command.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;
@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_id")
    private Long userId;

    @Column(name = "uuid", nullable = false, unique = true, updatable = false)
    private String uuid;

    @Column(name = "email", length = 255)
    private String email;

    @Column(name = "password", length = 255)
    private String password;

    @Enumerated(EnumType.STRING)
    @Column(name = "role")
    private Role role;

    @Column(name = "name")
    private String name;

    @Column(name = "phone")
    private String phone;

    @Column(name = "provider")
    private String provider;

    @Column(name = "profile_image_url")
    private String profileImageUrl;

    @Column(name = "provider_id")
    private String providerId;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "enabled")
    private Boolean enabled;

    @Column(name = "bio")
    private String bio;

    @Column(name = "full_name")
    private String fullName;

    @Enumerated(EnumType.STRING)
    @Column(name = "gender")
    private Gender gender;

    @PrePersist
    protected void onCreate() {
        this.uuid = UUID.randomUUID().toString();
    }

    public enum Role {
        MEMBER, TRAINER, ADMIN
    }

    public enum Gender {
        MALE, FEMALE
    }
}