package com.edf.teamedf.domain.user.command.application.service;

import com.edf.teamedf.domain.activity.command.domain.CharacterLevel;
import com.edf.teamedf.domain.activity.command.infrastructure.EcoActivityRepository;
import com.edf.teamedf.domain.community.command.infrastructure.CommentRepository;
import com.edf.teamedf.domain.community.command.infrastructure.PostLikeRepository;
import com.edf.teamedf.domain.community.command.infrastructure.PostRepository;
import com.edf.teamedf.domain.user.command.application.dto.profile.MeResponse;
import com.edf.teamedf.domain.user.command.application.dto.profile.ProfileUpdateRequest;
import com.edf.teamedf.domain.user.command.domain.User;
import com.edf.teamedf.domain.user.command.infrastructure.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserProfileService {

    private final UserRepository userRepository;
    private final PostRepository postRepository;
    private final CommentRepository commentRepository;
    private final PostLikeRepository postLikeRepository;
    private final EcoActivityRepository ecoActivityRepository;

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
