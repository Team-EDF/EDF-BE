package com.edf.teamedf.domain.challenge.command.infrastructure;

import com.edf.teamedf.domain.challenge.command.domain.ChallengeCheckIn;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

public interface ChallengeCheckInRepository extends JpaRepository<ChallengeCheckIn, Long> {

    boolean existsByUserChallenge_UserChallengeIdAndCheckDate(Long userChallengeId, LocalDate checkDate);

    List<ChallengeCheckIn> findByUserChallenge_UserChallengeIdInAndCheckDate(
            Collection<Long> userChallengeIds, LocalDate checkDate);
}
