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
        // Render keeps the PostgreSQL database between deployments. Hibernate's best-effort
        // ddl-auto update can leave an existing table behind when one of its DDL statements
        // fails. Apply the additive changes explicitly before any data seeder queries the tables.
        addColumn("assets", "hostname", "varchar(160)");
        addColumn("assets", "network_segment", "varchar(40)");
        addColumn("assets", "asset_type", "varchar(40) default 'VIRTUAL_MACHINE'");
        addColumn("assets", "zone", "varchar(80)");
        addColumn("assets", "internet_exposed", "boolean default false");
        addColumn("assets", "installed_products", "text");
        addColumn("assets", "maintenance_window", "varchar(120)");

        addColumn("approval_requests", "change_order_id", "bigint");
        addColumn("findings", "security_incident_id", "bigint");
        addColumn("scan_jobs", "automation_run_id", "bigint");

        addColumn("patches", "applicability_rule", "text");
        addColumn("patches", "applicability_rule_en", "text");
        addColumn("patches", "signature_status", "varchar(30) default 'VERIFIED'");
        addColumn("patches", "supersedes", "varchar(160)");
        addColumn("patches", "release_notes_zh", "text");
        addColumn("patches", "release_notes_en", "text");
        addColumn("patches", "signature_issuer", "varchar(240)");
        addColumn("patches", "signature_fingerprint", "varchar(160)");
        addColumn("patches", "integrity_verified_at", "timestamp with time zone");
        addColumn("patches", "vendor_advisory_url", "varchar(1000)");
        addColumn("patches", "prerequisites", "text");
        addColumn("patches", "prerequisites_en", "text");
        addColumn("patches", "install_command", "text");
        addColumn("patches", "uninstall_command", "text");
        addColumn("patches", "test_evidence", "text");
        addColumn("patches", "known_issues", "text");
        addColumn("patches", "known_issues_en", "text");

        addColumn("patch_deployments", "selection_mode", "varchar(30) default 'TASK'");
        addColumn("patch_deployments", "cidr_scopes", "text");
        addColumn("patch_deployments", "batch_size", "integer default 1");
        addColumn("patch_deployments", "concurrency", "integer default 1");
        addColumn("patch_deployments", "failure_threshold", "double precision default 5.0");
        addColumn("patch_deployments", "total_batches", "integer default 1");
        addColumn("patch_deployments", "scope_summary", "text");

        addColumn("remediation_tasks", "security_incident_id", "bigint");
        addColumn("remediation_tasks", "change_order_id", "bigint");
        addColumn("remediation_tasks", "last_retest_mode", "varchar(20)");
        addColumn("remediation_tasks", "last_retest_result", "varchar(20)");
        addColumn("remediation_tasks", "last_retest_comment", "varchar(1000)");
        addColumn("remediation_tasks", "last_retested_by", "varchar(120)");
        addColumn("remediation_tasks", "last_retested_at", "timestamp with time zone");

        // Retest runs intentionally have no patch deployment. Older Gazellio databases created this
        // column as NOT NULL, and Hibernate ddl-auto=update does not consistently remove that constraint.
        if (columnExists("deployment_targets", "deployment_id")) {
            jdbc.execute("alter table deployment_targets alter column deployment_id drop not null");
        }
        if (columnExists("patch_deployments", "task_id")) {
            jdbc.execute("alter table patch_deployments alter column task_id drop not null");
        }
        log.info("Schema compatibility verified: legacy Gazellio tables are upgraded");
    }

    private void addColumn(String table, String column, String definition) {
        if (tableExists(table) && !columnExists(table, column)) {
            jdbc.execute("alter table " + table + " add column " + column + " " + definition);
            log.info("Added missing legacy column {}.{}", table, column);
        }
    }

    private boolean tableExists(String table) {
        Integer count = jdbc.queryForObject("""
                select count(*) from information_schema.tables
                where lower(table_name) = lower(?)
                """, Integer.class, table);
        return count != null && count > 0;
    }

    private boolean columnExists(String table, String column) {
        Integer count = jdbc.queryForObject("""
                select count(*) from information_schema.columns
                where lower(table_name) = lower(?)
                  and lower(column_name) = lower(?)
                """, Integer.class, table, column);
        return count != null && count > 0;
    }
}
