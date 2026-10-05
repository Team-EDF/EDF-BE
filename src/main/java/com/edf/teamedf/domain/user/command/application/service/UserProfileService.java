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
    private final RefreshTokenStore refreshTokenStore;
    private final UserDataCleanupService userDataCleanupService;

    /** 탈퇴 의사 확인 문구. 앱(WithdrawScreen)의 CONFIRM_TEXT 와 같아야 한다. */
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
     * 회원 탈퇴. 확인 문구로 의사를 확인한 뒤 연결된 개인 데이터를 삭제하고, 계정을 익명화하고, 세션을 끊는다.
     *
     * 게시글·댓글은 지우지 않는다. 작성자 이름만 '탈퇴한 회원'으로 바뀐다.
     * 그 밖의 데이터(영수증·소비 기록, 활동 인증, 통계·랭킹, 이미지, 알림, 좋아요, 차단)는 모두 삭제한다.
     */
    @Transactional
    public void withdraw(Long userId, WithdrawRequest request) {
        User user = getUser(userId);

        if (user.isWithdrawn()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "이미 탈퇴한 계정입니다.");
        }

        // 관리자 계정은 탈퇴할 수 없다 (신고 처리 등 운영 권한을 가진 계정이 사라지는 것을 막는다).
        if (user.getRole() == User.Role.ADMIN) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "관리자 계정은 탈퇴할 수 없습니다.");
        }

        verifyConfirmText(request);

        String uuid = user.getUuid();

        // 벌크 삭제가 영속성 컨텍스트를 비우므로 이후에는 사용자를 다시 조회해서 익명화한다.
        userDataCleanupService.deleteUserData(userId);
        user = getUser(userId);
        user.withdraw(request != null ? request.reason() : null);

        // 남아 있는 refresh token 을 지워 다른 기기의 세션도 끊고,
        // 이미 발급된 access token 도 만료 전까지 쓰지 못하게 막는다.
        refreshTokenStore.delete(uuid);
        refreshTokenStore.markWithdrawn(uuid);
        log.info("회원 탈퇴 처리 완료 (userId={})", userId);
    }

    /**
     * 탈퇴 의사 확인. 로그인(access token)으로 본인임은 이미 확인됐으므로 비밀번호는 다시 묻지 않고,
     * 실수로 탈퇴하지 않도록 확인 문구만 받는다. 소셜 계정과 이메일 계정 모두 같은 방식이다.
     */
    private void verifyConfirmText(WithdrawRequest request) {
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

        int level = CharacterLevel.levelOf(totalSavedCarbon);

        return new MeResponse.Stats(
                postCount, commentCount, likedPostCount, activityCount,
                totalSavedCarbon, totalPoints, level);
    }

    private User getUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다."));
    }
}
