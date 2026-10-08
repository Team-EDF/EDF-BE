package com.edf.teamedf.domain.household.command.domain;

import com.edf.teamedf.domain.user.command.domain.User;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * 고지서 사진을 AI가 읽은 결과의 임시 기록.
 *
 * <p>사용자가 저장할 때 입력값이 "읽은 값 그대로"인지 서버가 확인해서 항목별로 고지서 인증 여부를 정한다.
 * (앱이 "인증됐다"고 주장하는 것을 믿지 않기 위한 장치) 몇 시간 뒤 만료되며 사진은 들어 있지 않다.</p>
 */
@Entity
@Table(name = "household_bill_reads")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class HouseholdBillRead {

    @Id
    @Column(name = "read_id", length = 36)
    private String readId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /** 읽은 사용 월 YYYY-MM */
    @Column(name = "bill_month", length = 7, nullable = false)
    private String billMonth;

    /** 읽은 값 JSON (electricity_kwh, electricity_krw, ...) */
    @Column(name = "values_json", columnDefinition = "TEXT")
    private String valuesJson;

    @Column(name = "fingerprint", length = 64)
    private String fingerprint;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
