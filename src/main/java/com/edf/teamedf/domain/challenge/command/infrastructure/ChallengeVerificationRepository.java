package com.edf.teamedf.domain.challenge.command.infrastructure;

import com.edf.teamedf.domain.challenge.command.domain.ChallengeVerification;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;

public interface ChallengeVerificationRepository extends JpaRepository<ChallengeVerification, Long> {

    boolean existsByFingerprint(String fingerprint);

    long countByUserChallenge_UserChallengeId(Long userChallengeId);

    /** 기준 시각 이후에 인증 보너스를 받은 횟수 (일일 보너스 한도 계산용). */
    long countByUser_UserIdAndBonusPointsGreaterThanAndCreatedAtGreaterThanEqual(
            Long userId, Integer minBonus, LocalDateTime from);
}
