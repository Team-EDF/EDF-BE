package com.edf.teamedf.domain.dashboard.command.infrastructure;

import com.edf.teamedf.domain.dashboard.command.domain.ConsumptionItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface ConsumptionItemRepository extends JpaRepository<ConsumptionItem, Long> {

    void deleteByRecord_RecordId(Long recordId);

    // 영수증 목록의 품목명을 한 번에 가져온다 (영수증마다 조회하는 N+1 방지).
    List<ConsumptionItem> findByRecord_RecordIdIn(Collection<Long> recordIds);
}
