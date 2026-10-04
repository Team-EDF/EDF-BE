package com.edf.teamedf.domain.household.command.domain;

import com.edf.teamedf.domain.user.command.domain.User;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 한 달 가정 에너지(관리비) 기록: 전기·수도·도시가스·지역난방의 사용량과 금액, 계산된 탄소.
 *
 * <p>사용자당 월 1건이다(다시 저장하면 덮어씀). 값은 직접 입력하거나 고지서 사진에서 읽은 값이다.
 * 항목별로 "고지서로 인증된 값인지"를 기록하고(수정하지 않은 값만 인증), 절감 포인트는 인증된 달끼리의 비교에만 준다.
 * 사진은 저장하지 않는다. 같은 고지서의 재사용을 막기 위해 읽은 값의 지문(해시)만 남긴다.</p>
 */
@Entity
@Table(name = "household_bills",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_household_bill_user_month", columnNames = {"user_id", "bill_month"})
        },
        indexes = {
                @Index(name = "idx_household_bill_fingerprint", columnList = "bill_fingerprint")
        })
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class HouseholdBill {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "bill_id")
    private Long billId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /** 사용(검침) 월의 1일 */
    @Column(name = "bill_month", nullable = false)
    private LocalDate billMonth;

    @Column(name = "electricity_kwh")
    private Double electricityKwh;
    @Column(name = "electricity_krw")
    private Integer electricityKrw;
    @Column(name = "water_m3")
    private Double waterM3;
    @Column(name = "water_krw")
    private Integer waterKrw;
    @Column(name = "gas_m3")
    private Double gasM3;
    @Column(name = "gas_krw")
    private Integer gasKrw;
    @Column(name = "heat_gcal")
    private Double heatGcal;
    @Column(name = "heat_krw")
    private Integer heatKrw;

    /** 고지서로 인증된 항목 키들 (쉼표로 구분: electricity,water,gas,heat). 없으면 빈 문자열 */
    @Column(name = "verified_keys", length = 60)
    private String verifiedKeys;

    /** 인증된 고지서 값의 지문 (같은 고지서를 다른 계정에서 또 쓰는 것을 막는다). 인증이 없으면 null */
    @Column(name = "bill_fingerprint", length = 64)
    private String billFingerprint;

    /** 이 달의 총 탄소 (kgCO2eq) */
    @Column(name = "carbon_kg")
    private Float carbonKg;

    /** 항목별 탄소 내역 JSON (AI 계산 결과의 items) */
    @Column(name = "carbon_json", columnDefinition = "TEXT")
    private String carbonJson;

    @Column(name = "estimated")
    private Boolean estimated;

    /** 절감 포인트 지급 상태 JSON: {"electricity":{"tier":2,"points":80}, ...} */
    @Column(name = "reward_state", columnDefinition = "TEXT")
    private String rewardState;

    @Column(name = "reward_points")
    private Integer rewardPoints;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    /** 입력값과 계산 결과를 한꺼번에 덮어쓴다. */
    public void apply(Double electricityKwh, Integer electricityKrw, Double waterM3, Integer waterKrw,
                      Double gasM3, Integer gasKrw, Double heatGcal, Integer heatKrw,
                      String verifiedKeys, String billFingerprint,
                      Float carbonKg, String carbonJson, boolean estimated) {
        this.electricityKwh = electricityKwh;
        this.electricityKrw = electricityKrw;
        this.waterM3 = waterM3;
        this.waterKrw = waterKrw;
        this.gasM3 = gasM3;
        this.gasKrw = gasKrw;
        this.heatGcal = heatGcal;
        this.heatKrw = heatKrw;
        this.verifiedKeys = verifiedKeys;
        this.billFingerprint = billFingerprint;
        this.carbonKg = carbonKg;
        this.carbonJson = carbonJson;
        this.estimated = estimated;
    }

    public void updateReward(String rewardState, int rewardPoints) {
        this.rewardState = rewardState;
        this.rewardPoints = rewardPoints;
    }

    public Double usageOf(HouseholdSavings.Utility utility) {
        return switch (utility) {
            case ELECTRICITY -> electricityKwh;
            case WATER -> waterM3;
            case GAS -> gasM3;
            case HEAT -> heatGcal;
        };
    }

    public Integer krwOf(HouseholdSavings.Utility utility) {
        return switch (utility) {
            case ELECTRICITY -> electricityKrw;
            case WATER -> waterKrw;
            case GAS -> gasKrw;
            case HEAT -> heatKrw;
        };
    }

    public boolean isVerified(HouseholdSavings.Utility utility) {
        if (verifiedKeys == null || verifiedKeys.isBlank()) {
            return false;
        }
        for (String key : verifiedKeys.split(",")) {
            if (key.trim().equals(utility.key())) {
                return true;
            }
        }
        return false;
    }
}
