package com.edf.teamedf.common.util;

import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** Optional calendar filter. End boundaries are exclusive. */
public final class CalendarMonth {
    private CalendarMonth() {}
    public static YearMonth parse(String value) {
        if (value == null || value.isBlank()) return null;
        try { return YearMonth.parse(value); }
        catch (DateTimeParseException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "사용월은 YYYY-MM 형식이어야 합니다.");
        }
    }
}
