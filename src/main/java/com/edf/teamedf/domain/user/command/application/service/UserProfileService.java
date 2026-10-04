package com.edf.teamedf.domain.user.command.application.service;

import com.edf.teamedf.domain.activity.command.domain.CharacterLevel;
import com.edf.teamedf.domain.activity.command.infrastructure.EcoActivityRepository;
import com.edf.teamedf.domain.community.command.infrastructure.CommentRepository;
import com.edf.teamedf.domain.community.command.infrastructure.PostLikeRepository;
import com.edf.teamedf.domain.community.command.infrastructure.PostRepository;
import com.edf.teamedf.common.security.auth.RefreshTokenStore;
import com.edf.teamedf.domain.user.command.application.dto.account.WithdrawRequest;
import com.edf.teamedf.domain.user.command.application.dto.profile.MeResponse;
import com.edf.teamedf.domain.user.command.application.dto.profile.ProfileUpdateRequest;
import com.edf.teamedf.domain.user.command.domain.User;
import com.edf.teamedf.domain.user.command.infrastructure.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserProfileService {

    private final UserRepository userRepository;
    private final PostRepository postRepository;
    private final CommentRepository commentRepository;
    private final PostLikeRepository postLikeRepository;
    private final EcoActivityRepository ecoActivityRepository;
    private final PasswordEncoder passwordEncoder;
    private final RefreshTokenStore refreshTokenStore;

    /** 비밀번호가 없는 계정(소셜 로그인)에서 요구하는 확인 문구. */
    private static final String CONFIRM_TEXT = "탈퇴합니다";

    public MeResponse getMe(Long userId) {
        return MeResponse.of(getUser(userId), buildStats(userId));
    }

    @Transactional
    public MeResponse updateMe(Long userId, ProfileUpdateRequest request) {
        User user = getUser(userId);
        user.updateProfile(
                request.name(),
                request.bio(),
                request.phone(),
                request.profileImageUrl(),
                request.gender(),
                request.address(),
                request.addressDetail(),
                request.zipCode()
        );
        return MeResponse.of(user, buildStats(userId));
    }

    /**
     * 회원 탈퇴. 본인 확인을 거친 뒤 계정을 익명화하고 세션을 끊는다.
     *
     * 게시글·댓글은 지우지 않는다. 작성자 이름만 '탈퇴한 회원'으로 바뀐다.
     */
    @Transactional
    public void withdraw(Long userId, WithdrawRequest request) {
        User user = getUser(userId);

        if (user.isWithdrawn()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "이미 탈퇴한 계정입니다.");
        }

        verifyOwner(user, request);

        String uuid = user.getUuid();
        user.withdraw(request != null ? request.reason() : null);

        // 남아 있는 refresh token 을 지워 다른 기기의 세션도 끊는다.
        refreshTokenStore.delete(uuid);
        log.info("회원 탈퇴 처리 완료 (userId={})", userId);
    }

    /**
     * 본인 확인. 비밀번호가 있는 계정은 비밀번호로, 소셜 계정은 확인 문구로 확인한다.
     * 소셜 계정에는 대조할 비밀번호가 없어서 비밀번호만 요구하면 탈퇴 자체가 불가능해진다.
     */
    private void verifyOwner(User user, WithdrawRequest request) {
        String storedPassword = user.getPassword();

        if (storedPassword != null && !storedPassword.isBlank()) {
            String input = request != null ? request.password() : null;
            if (input == null || input.isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "비밀번호를 입력해 주세요.");
            }
            if (!passwordEncoder.matches(input, storedPassword)) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "비밀번호가 일치하지 않습니다.");
            }
            return;
        }

        String confirmText = request != null ? request.confirmText() : null;
        if (confirmText == null || !CONFIRM_TEXT.equals(confirmText.trim())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "확인 문구 '" + CONFIRM_TEXT + "'를 입력해 주세요.");
        }
    }

    private MeResponse.Stats buildStats(Long userId) {
        long postCount = postRepository.countByUser_UserIdAndIsDeletedFalse(userId);
        long commentCount = commentRepository.countByUser_UserIdAndIsDeletedFalse(userId);
        long likedPostCount = postLikeRepository.countByUser_UserId(userId);
        long activityCount = ecoActivityRepository.countByUser_UserId(userId);

        Double carbon = ecoActivityRepository.sumSavedCarbonByUserId(userId);
        float totalSavedCarbon = carbon == null ? 0f : carbon.floatValue();

        Long points = ecoActivityRepository.sumPointsByUserId(userId);
        long totalPoints = points == null ? 0L : points;

        int level = CharacterLevel.levelOf(totalSavedCarbon, totalPoints);

        return new MeResponse.Stats(
                postCount, commentCount, likedPostCount, activityCount,
                totalSavedCarbon, totalPoints, level);
    }

    private User getUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다."));
    }
}
