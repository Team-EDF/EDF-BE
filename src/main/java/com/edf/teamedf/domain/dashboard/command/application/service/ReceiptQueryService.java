package com.edf.teamedf.domain.dashboard.command.application.service;

import com.edf.teamedf.domain.dashboard.command.application.dto.ReceiptRecordResponse;
import com.edf.teamedf.domain.dashboard.command.domain.ConsumptionItem;
import com.edf.teamedf.domain.dashboard.command.domain.ConsumptionRecord;
import com.edf.teamedf.domain.dashboard.command.infrastructure.ConsumptionItemRepository;
import com.edf.teamedf.domain.dashboard.command.infrastructure.ConsumptionRecordRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReceiptQueryService {

    // 업로드 직후(WAITING_CONFIRM)와 확정 후(SUCCESS) 영수증만 목록에 보인다.
    private static final List<String> VISIBLE_STATUSES = List.of("WAITING_CONFIRM", "SUCCESS");
    private static final int MAX_PAGE_SIZE = 100;

    private final ConsumptionRecordRepository consumptionRecordRepository;
    private final ConsumptionItemRepository consumptionItemRepository;

    public Page<ReceiptRecordResponse> getMyReceipts(Long userId, int page, int size) {
        PageRequest pageable = PageRequest.of(
                Math.max(page, 0),
                Math.min(Math.max(size, 1), MAX_PAGE_SIZE),
                Sort.by(Sort.Direction.DESC, "createdAt"));

        Page<ConsumptionRecord> records = consumptionRecordRepository
                .findByUser_UserIdAndOcrStatusIn(userId, VISIBLE_STATUSES, pageable);

        List<Long> recordIds = records.getContent().stream().map(ConsumptionRecord::getRecordId).toList();
        Map<Long, List<String>> itemNamesByRecord = new LinkedHashMap<>();
        if (!recordIds.isEmpty()) {
            for (ConsumptionItem item : consumptionItemRepository.findByRecord_RecordIdIn(recordIds)) {
                if (item.getSourceMsg() == null || item.getSourceMsg().isBlank()) continue;
                itemNamesByRecord
                        .computeIfAbsent(item.getRecord().getRecordId(), k -> new ArrayList<>())
                        .add(item.getSourceMsg());
            }
        }

        return records.map(record -> ReceiptRecordResponse.of(
                record, itemNamesByRecord.getOrDefault(record.getRecordId(), List.of())));
    }
}
