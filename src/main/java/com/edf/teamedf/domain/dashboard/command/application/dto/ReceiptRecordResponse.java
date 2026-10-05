package com.edf.teamedf.domain.dashboard.command.application.dto;

import com.edf.teamedf.domain.dashboard.command.domain.ConsumptionRecord;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** 영수증 캘린더에 보여줄 영수증 한 건. */
public record ReceiptRecordResponse(
        Long recordId,
        String merchantName,
        Integer totalAmount,
        Float totalCarbonKg,
        LocalDate recordDate,
        LocalDateTime createdAt,
        String imageUrl,
        String ocrStatus,
        List<String> items
) {
    public static ReceiptRecordResponse of(ConsumptionRecord record, List<String> itemNames) {
        return new ReceiptRecordResponse(
                record.getRecordId(),
                record.getMerchantName(),
                record.getTotalAmount(),
                record.getTotalCarbonKg(),
                record.getRecordDate(),
                record.getCreatedAt(),
                record.getImageUrl(),
                record.getOcrStatus(),
                itemNames
        );
    }
}
