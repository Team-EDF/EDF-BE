package com.edf.teamedf.domain.user.command.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDate;
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

    @Column(name = "address")
    private String address;

    @Column(name = "address_detail")
    private String addressDetail;

    @Column(name = "zip_code", length = 10)
    private String zipCode;

    @Column(name = "nickname", length = 30, unique = true)
    private String nickname;

    @Column(name = "birth_date")
    private LocalDate birthDate;

    @Column(name = "terms_agreed")
    private Boolean termsAgreed;

    @Column(name = "privacy_agreed")
    private Boolean privacyAgreed;

    @Column(name = "marketing_agreed")
    private Boolean marketingAgreed;

    /** 탈퇴 시각. null 이면 정상 회원. */
    @Column(name = "withdrawn_at")
    private LocalDateTime withdrawnAt;

    /** 탈퇴 사유 (선택 입력, 통계용). */
    @Column(name = "withdraw_reason", length = 200)
    private String withdrawReason;

    public void updatePassword(String encodedPassword) {
        this.password = encodedPassword;
    }

    /**
     * 프로필 수정. null 로 전달된 필드는 변경하지 않는다(부분 수정).
     */
    public void updateProfile(String name,
                              String bio,
                              String phone,
                              String profileImageUrl,
                              Gender gender,
                              String address,
                              String addressDetail,
                              String zipCode) {
        if (name != null && !name.isBlank()) {
            this.name = name.trim();
        }
        if (bio != null) {
            this.bio = bio.isBlank() ? null : bio.trim();
        }
        if (phone != null) {
            this.phone = phone.isBlank() ? null : phone.trim();
        }
        if (profileImageUrl != null) {
            this.profileImageUrl = profileImageUrl.isBlank() ? null : profileImageUrl.trim();
        }
        if (gender != null) {
            this.gender = gender;
        }
        if (address != null) {
            this.address = address.isBlank() ? null : address.trim();
        }
        if (addressDetail != null) {
            this.addressDetail = addressDetail.isBlank() ? null : addressDetail.trim();
        }
        if (zipCode != null) {
            this.zipCode = zipCode.isBlank() ? null : zipCode.trim();
        }
    }

    public boolean isWithdrawn() {
        return this.withdrawnAt != null;
    }

    /**
     * 회원 탈퇴 (소프트 탈퇴 + 즉시 익명화).
     *
     * 행 자체는 남긴다. 게시글·댓글·인증 기록이 이 행을 참조하고 있어서, 지우면
     * 다른 사람이 그 글에 단 댓글까지 함께 사라지기 때문이다. 대신 식별에 쓰이는
     * 개인정보는 이 자리에서 모두 지우고, 화면에 노출되는 이름만 '탈퇴한 회원'으로 남긴다.
     *
     * 이메일을 비우므로 로그인 조회(findByEmail)에 걸리지 않고, 같은 이메일로 재가입할 수 있다.
     * 닉네임은 unique 제약이 있어 userId 를 섞은 값으로 바꾼다.
     */
    public void withdraw(String reason) {
        this.withdrawnAt = LocalDateTime.now();
        this.withdrawReason = (reason == null || reason.isBlank()) ? null : reason.trim();
        this.enabled = false;

        this.email = null;
        this.password = null;
        this.phone = null;
        this.providerId = null;
        this.profileImageUrl = null;
        this.bio = null;
        this.fullName = null;
        this.address = null;
        this.addressDetail = null;
        this.zipCode = null;
        this.birthDate = null;
        this.gender = Gender.NONE;
        this.marketingAgreed = Boolean.FALSE;

        this.name = WITHDRAWN_NAME;
        this.nickname = "withdrawn_" + this.userId;
    }

    /** 게시글·댓글 작성자 자리에 노출되는 이름. */
    public static final String WITHDRAWN_NAME = "탈퇴한 회원";

    @PrePersist
    protected void onCreate() {
        this.uuid = UUID.randomUUID().toString();
    }

    public enum Role {
        MEMBER, TRAINER, ADMIN
    }

    public enum Gender {
        MALE, FEMALE, NONE
    }
}