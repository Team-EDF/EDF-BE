package com.edf.teamedf.domain.dashboard.command.infrastructure;

import com.edf.teamedf.domain.dashboard.command.domain.ConsumptionItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ConsumptionItemRepository extends JpaRepository<ConsumptionItem, Long> {

    void deleteByRecord_RecordId(Long recordId);
}
