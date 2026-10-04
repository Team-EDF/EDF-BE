package com.edf.teamedf.domain.household.command.infrastructure;

import com.edf.teamedf.domain.household.command.domain.HouseholdBill;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface HouseholdBillRepository extends JpaRepository<HouseholdBill, Long> {

    List<HouseholdBill> findByUser_UserIdOrderByBillMonthDesc(Long userId);

    Optional<HouseholdBill> findByUser_UserIdAndBillMonth(Long userId, LocalDate billMonth);

    /** 다른 사용자가 이미 같은 고지서(지문)를 인증에 썼는지. */
    boolean existsByBillFingerprintAndUser_UserIdNot(String billFingerprint, Long userId);
}
