package com.edf.teamedf.domain.community.command.infrastructure;

import com.edf.teamedf.domain.community.command.domain.Report;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ReportRepository extends JpaRepository<Report, Long> {

    Optional<Report> findByReporter_UserIdAndTargetTypeAndTargetId(
            Long reporterId, Report.TargetType targetType, Long targetId);

    Page<Report> findAllByOrderByCreatedAtDesc(Pageable pageable);

    Page<Report> findAllByStatusOrderByCreatedAtDesc(Report.Status status, Pageable pageable);
}
