package com.edf.teamedf.domain.household.command.application.dto;

/**
 * 한 달 관리비 저장 요청 (직접 입력, 또는 고지서에서 읽은 값을 확인·수정한 것).
 *
 * <p>사용량이 있으면 사용량으로 탄소를 계산하고, 금액만 있으면 금액으로 추정한다.
 * {@code readId}가 있고 값이 읽은 값과 같은 항목만 고지서로 인증된 값으로 기록된다.</p>
 *
 * @param month  사용 월 YYYY-MM (최근 13개월 이내, 이번 달까지)
 * @param readId 고지서 읽기 결과 식별자 (직접 입력이면 null)
 */
public record SaveBillRequest(
        String month,
        Double electricityKwh,
        Integer electricityKrw,
        Double waterM3,
        Integer waterKrw,
        Double gasM3,
        Integer gasKrw,
        Double heatGcal,
        Integer heatKrw,
        String readId
) {
}
