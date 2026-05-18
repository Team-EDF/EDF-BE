package com.edf.teamedf.domain.user.command.application.service;

import com.edf.teamedf.domain.user.query.dto.user.UserSearchCondition;
import com.edf.teamedf.domain.user.query.dto.user.UserSummaryResponse;
import com.edf.teamedf.domain.user.command.infrastructure.UserRepository;
import com.edf.teamedf.domain.user.query.repository.UserSpecification;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserQueryService {

    private final UserRepository userRepository;


    public Page<UserSummaryResponse> getUsers(Pageable pageable) {
        return userRepository.findAll(pageable).map(UserSummaryResponse::from);
    }

    public Page<UserSummaryResponse> searchUsers(UserSearchCondition condition, Pageable pageable) {
        return userRepository.findAll(UserSpecification.withCondition(condition), pageable)
                .map(UserSummaryResponse::from);
    }

    public UserSummaryResponse getUser(Long userId) {
        return userRepository.findById(userId)
                .map(UserSummaryResponse::from)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다."));
    }
}
