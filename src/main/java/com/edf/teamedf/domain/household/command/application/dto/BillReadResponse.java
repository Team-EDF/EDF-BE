package com.edf.teamedf.domain.household.command.application.dto;

import java.util.Map;

/**
 * 고지서 사진 읽기 결과 (저장 전 "미리 채우기" 용도).
 *
 * @param readId    읽은 결과 식별자. 이 값으로 저장하면 읽은 값을 수정하지 않은 항목은 "고지서 인증"으로 인정된다. 읽지 못했으면 null
 * @param readable  값을 읽었는지
 * @param code      OK | NOT_A_BILL | UNREADABLE | EDITED | MONTH_UNKNOWN | MONTH_OUT_OF_RANGE | NO_VALUES
 * @param billMonth 읽은 사용 월 YYYY-MM
 * @param values    읽은 값 (electricity_kwh, electricity_krw, water_m3, water_krw, gas_m3, gas_krw, heat_gcal, heat_krw)
 */
public record BillReadResponse(
        String readId,
        boolean readable,
        String code,
        String message,
        String billMonth,
        Map<String, Object> values,
        Integer totalKrw,
        String note
) {
}
