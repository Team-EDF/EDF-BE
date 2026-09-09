package com.edf.teamedf.domain.user.command.application.dto.profile;

import com.edf.teamedf.domain.user.command.domain.User;
import jakarta.validation.constraints.Size;

/** 프로필 부분 수정 요청. null 필드는 변경하지 않는다. */
public record ProfileUpdateRequest(
        @Size(max = 30, message = "이름은 30자 이하여야 합니다.")
        String name,

        @Size(max = 200, message = "소개는 200자 이하여야 합니다.")
        String bio,

        @Size(max = 20, message = "전화번호는 20자 이하여야 합니다.")
        String phone,

        String profileImageUrl,
        User.Gender gender,
        String address,
        String addressDetail,

        @Size(max = 10, message = "우편번호는 10자 이하여야 합니다.")
        String zipCode
) {
}
