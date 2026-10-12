package com.edf.teamedf.common.config;

import com.edf.teamedf.domain.activity.command.domain.EcoCategory;
import com.edf.teamedf.domain.image.command.domain.ReferenceType;
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
        sync("eco_activities", "category", CONSTRAINT, EcoCategory.values());
        // 업로드 이미지 참조 유형에 ECO_ACTIVITY(활동 사진 인증)를 추가했으므로 같은 방식으로 맞춘다.
        sync("uploaded_images", "reference_type", "uploaded_images_reference_type_check", ReferenceType.values());
    }

    private void sync(String table, String column, String constraint, Enum<?>[] values) {
        String allowed = Arrays.stream(values)
                .map(c -> "'" + c.name() + "'")
                .collect(Collectors.joining(", "));
        try {
            jdbcTemplate.execute("ALTER TABLE " + table + " DROP CONSTRAINT IF EXISTS " + constraint);
            jdbcTemplate.execute("ALTER TABLE " + table + " ADD CONSTRAINT " + constraint
                    + " CHECK (" + column + " IN (" + allowed + "))");
            log.info("{}.{} 제약을 enum 값 목록과 맞췄습니다: {}", table, column, allowed);
        } catch (Exception e) {
            log.warn("{}.{} 제약 동기화 실패 (서버 기동은 계속): {}", table, column, e.toString());
        }
    }
}
