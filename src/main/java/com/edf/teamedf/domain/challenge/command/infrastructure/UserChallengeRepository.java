package com.edf.teamedf.domain.challenge.command.infrastructure;

import com.edf.teamedf.domain.challenge.command.domain.UserChallenge;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface UserChallengeRepository extends JpaRepository<UserChallenge, Long> {

    List<UserChallenge> findByUser_UserIdAndWeekStartOrderBySlotAsc(Long userId, LocalDate weekStart);

    Optional<UserChallenge> findByUserChallengeIdAndUser_UserId(Long userChallengeId, Long userId);

    List<UserChallenge> findByUser_UserIdAndWeekStartAndStatusAndVerification(
            Long userId, LocalDate weekStart, String status, String verification);
}
