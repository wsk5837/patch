package com.gazellio.platform.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Applies small forward-compatible schema fixes that Hibernate update cannot reliably infer. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@RequiredArgsConstructor
@Slf4j
public class SchemaCompatibilityMigration implements ApplicationRunner {
    private final JdbcTemplate jdbc;

    @Override
    public void run(ApplicationArguments args) {
        migrate();
    }

    public void migrate() {
        Integer columns = jdbc.queryForObject("""
                select count(*) from information_schema.columns
                where lower(table_name) = 'deployment_targets'
                  and lower(column_name) = 'deployment_id'
                """, Integer.class);
        if (columns == null || columns == 0) return;

        // Retest runs intentionally have no patch deployment. Older Gazellio databases created this
        // column as NOT NULL, and Hibernate ddl-auto=update does not consistently remove that constraint.
        jdbc.execute("alter table deployment_targets alter column deployment_id drop not null");
        Integer deploymentTaskColumns = jdbc.queryForObject("""
                select count(*) from information_schema.columns
                where lower(table_name) = 'patch_deployments'
                  and lower(column_name) = 'task_id'
                """, Integer.class);
        if (deploymentTaskColumns != null && deploymentTaskColumns > 0) {
            jdbc.execute("alter table patch_deployments alter column task_id drop not null");
        }
        log.info("Schema compatibility verified: standalone batch deployments and retest targets are supported");
    }
}
