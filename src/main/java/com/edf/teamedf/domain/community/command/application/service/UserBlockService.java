package com.edf.teamedf.domain.community.command.application.service;

import com.edf.teamedf.domain.community.command.application.dto.block.BlockedUserResponse;
import com.edf.teamedf.domain.community.command.domain.UserBlock;
import com.edf.teamedf.domain.community.command.infrastructure.UserBlockRepository;
import com.edf.teamedf.domain.user.command.domain.User;
import com.edf.teamedf.domain.user.command.infrastructure.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** 사용자 차단. 차단한 사용자의 게시글·댓글은 조회 시 서버에서 제외한다. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserBlockService {

    private final UserBlockRepository userBlockRepository;
    private final UserRepository userRepository;

    /** 차단. 이미 차단한 사용자면 아무 것도 하지 않는다 (멱등). */
    @Transactional
    public void block(Long blockerId, Long blockedId) {
        if (blockerId.equals(blockedId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "자기 자신은 차단할 수 없습니다.");
        }
        if (userBlockRepository.existsByBlocker_UserIdAndBlocked_UserId(blockerId, blockedId)) {
            return;
        }
        User blocker = getUser(blockerId);
        User blocked = getUser(blockedId);
        userBlockRepository.save(UserBlock.builder().blocker(blocker).blocked(blocked).build());
    }

    /** 차단 해제. 차단한 적이 없어도 성공으로 본다 (멱등). */
    @Transactional
    public void unblock(Long blockerId, Long blockedId) {
        userBlockRepository.findByBlocker_UserIdAndBlocked_UserId(blockerId, blockedId)
                .ifPresent(userBlockRepository::delete);
    }

    public List<BlockedUserResponse> getBlockedUsers(Long blockerId) {
        return userBlockRepository.findAllWithBlockedByBlockerId(blockerId).stream()
                .map(BlockedUserResponse::from)
                .toList();
    }

    /** viewer 가 차단한 사용자 id 집합. 비로그인이면 빈 집합. */
    public Set<Long> getBlockedUserIds(Long viewerId) {
        if (viewerId == null) {
            return Set.of();
        }
        return new HashSet<>(userBlockRepository.findBlockedUserIds(viewerId));
    }

    private User getUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다."));
    }
}
