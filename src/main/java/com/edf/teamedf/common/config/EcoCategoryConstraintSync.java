package com.edf.teamedf.common.config;

import com.edf.teamedf.domain.activity.command.domain.EcoCategory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * eco_activities.category 의 CHECK 제약을 {@link EcoCategory} 값 목록과 맞춘다.
 *
 * <p>Hibernate(ddl-auto: update)는 enum 컬럼에 "허용 값 목록" CHECK 제약을 만들지만, 이미 만들어진
 * 제약은 enum에 값이 추가돼도 갱신하지 않는다. 그래서 카테고리를 추가하면(예: CHALLENGE)
 * 기존 DB에서는 INSERT 가 제약 위반(500)으로 실패한다. 이 프로젝트에는 마이그레이션 도구가 없어서,
 * 서버가 켜질 때마다 제약을 현재 enum 값으로 다시 만든다. 여러 번 실행해도 안전하다(idempotent).</p>
 *
 * <p>실패해도 서버 기동은 막지 않는다(경고만 남김).</p>
 */
@Component
public class EcoCategoryConstraintSync implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(EcoCategoryConstraintSync.class);
    private static final String CONSTRAINT = "eco_activities_category_check";

    private final JdbcTemplate jdbcTemplate;

    public EcoCategoryConstraintSync(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        String allowed = Arrays.stream(EcoCategory.values())
                .map(c -> "'" + c.name() + "'")
                .collect(Collectors.joining(", "));
        try {
            jdbcTemplate.execute("ALTER TABLE eco_activities DROP CONSTRAINT IF EXISTS " + CONSTRAINT);
            jdbcTemplate.execute("ALTER TABLE eco_activities ADD CONSTRAINT " + CONSTRAINT
                    + " CHECK (category IN (" + allowed + "))");
            log.info("eco_activities.category 제약을 enum 값 목록과 맞췄습니다: {}", allowed);
        } catch (Exception e) {
            log.warn("eco_activities.category 제약 동기화 실패 (서버 기동은 계속): {}", e.toString());
        }
    }
}
