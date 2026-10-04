package com.edf.teamedf.domain.household.command.infrastructure;

import com.edf.teamedf.domain.household.command.domain.HouseholdBillRead;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;

public interface HouseholdBillReadRepository extends JpaRepository<HouseholdBillRead, String> {

    /** 만료된(오래된) 읽기 기록 정리용. */
    long deleteByCreatedAtBefore(LocalDateTime before);
}
