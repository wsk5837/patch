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
        addColumn("assets", "asset_type", "varchar(40) default 'UNCLASSIFIED'");
        addColumn("assets", "zone", "varchar(80)");
        addColumn("assets", "internet_exposed", "boolean default false");
        addColumn("assets", "installed_products", "text");
        addColumn("assets", "maintenance_window", "varchar(120)");
        addColumn("assets", "source_system", "varchar(30) default 'LOCAL'");
        addColumn("assets", "cmdb_item_id", "varchar(80)");
        addColumn("assets", "cmdb_class_key", "varchar(80)");
        addColumn("assets", "cmdb_class_name", "varchar(120)");
        addColumn("assets", "cmdb_state", "varchar(120)");
        addColumn("assets", "cmdb_locked", "boolean");
        addColumn("assets", "cmdb_enabled", "boolean");
        addColumn("assets", "cmdb_auto_discovery", "boolean");
        addColumn("assets", "cmdb_updated_at", "timestamp with time zone");
        addColumn("assets", "cmdb_synced_at", "timestamp with time zone");
        normalizeInternetExposure();
        normalizeAssetSource();

        addColumn("user_accounts", "department", "varchar(120)");
        addColumn("user_accounts", "employee_no", "varchar(80)");
        addColumn("user_accounts", "phone", "varchar(40)");
        addColumn("user_accounts", "account_type", "varchar(30) default 'LOCAL'");
        addColumn("user_accounts", "locked", "boolean default false");
        addColumn("user_accounts", "failed_login_attempts", "integer default 0");
        addColumn("user_accounts", "last_login_at", "timestamp with time zone");
        addColumn("user_accounts", "password_changed_at", "timestamp with time zone");
        addColumn("user_accounts", "updated_at", "timestamp with time zone default current_timestamp");
        addColumn("access_roles", "description_zh", "text");
        addColumn("access_roles", "description_en", "text");
        addColumn("access_roles", "data_scope", "varchar(30) default 'ALL'");
        migrateUserRoleAssignments();

        upgradeSeverityConstraint();

        // Vulnerability-library knowledge fields. Keep them nullable so an existing Render
        // database can be upgraded without rewriting all rows in a blocking DDL statement.
        addColumn("vulnerability_definitions", "cwe_id", "varchar(40)");
        addColumn("vulnerability_definitions", "cvss_vector", "varchar(220)");
        addColumn("vulnerability_definitions", "attack_vector", "varchar(30)");
        addColumn("vulnerability_definitions", "attack_complexity", "varchar(30)");
        addColumn("vulnerability_definitions", "privileges_required", "varchar(30)");
        addColumn("vulnerability_definitions", "user_interaction", "varchar(30)");
        addColumn("vulnerability_definitions", "exploit_maturity", "varchar(40)");
        addColumn("vulnerability_definitions", "affected_components_zh", "text");
        addColumn("vulnerability_definitions", "affected_components_en", "text");
        addColumn("vulnerability_definitions", "affected_version_range_zh", "text");
        addColumn("vulnerability_definitions", "affected_version_range_en", "text");
        addColumn("vulnerability_definitions", "fixed_version", "varchar(160)");
        addColumn("vulnerability_definitions", "impact_zh", "text");
        addColumn("vulnerability_definitions", "impact_en", "text");
        addColumn("vulnerability_definitions", "scanner_rule_id", "varchar(120)");
        addColumn("vulnerability_definitions", "detection_guidance_zh", "text");
        addColumn("vulnerability_definitions", "detection_guidance_en", "text");
        addColumn("vulnerability_definitions", "remediation_guidance_zh", "text");
        addColumn("vulnerability_definitions", "remediation_guidance_en", "text");
        addColumn("vulnerability_definitions", "mitigation_zh", "text");
        addColumn("vulnerability_definitions", "mitigation_en", "text");
        addColumn("vulnerability_definitions", "virtual_patch_available", "boolean");
        addColumn("vulnerability_definitions", "virtual_patch_guidance_zh", "text");
        addColumn("vulnerability_definitions", "virtual_patch_guidance_en", "text");
        addColumn("vulnerability_definitions", "evidence_requirements_zh", "text");
        addColumn("vulnerability_definitions", "evidence_requirements_en", "text");
        addColumn("vulnerability_definitions", "intelligence_sources", "text");
        addColumn("vulnerability_definitions", "last_analyzed_at", "timestamp with time zone");

        addColumn("approval_requests", "change_order_id", "bigint");
        addColumn("findings", "security_incident_id", "bigint");
        addColumn("findings", "compensating_control", "text");
        addColumn("findings", "residual_risk", "text");
        addColumn("findings", "exemption_approved_by", "varchar(120)");
        addColumn("findings", "exemption_approved_at", "timestamp with time zone");
        addColumn("scan_jobs", "automation_run_id", "bigint");
        addColumn("audit_events", "source_ip", "varchar(80)");
        addColumn("audit_events", "user_agent", "varchar(500)");

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
        addColumn("patch_deployments", "change_order_id", "bigint");
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
        normalizeTaskNumbers();
        reconcileOperationalAssetReferences();

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

    private void normalizeInternetExposure() {
        if (!columnExists("assets", "internet_exposed")) return;
        jdbc.update("update assets set internet_exposed = false where internet_exposed is null");
        jdbc.execute("alter table assets alter column internet_exposed set default false");
        jdbc.execute("alter table assets alter column internet_exposed set not null");
    }

    private void migrateUserRoleAssignments() {
        if (!tableExists("user_accounts") || !tableExists("access_roles")) return;
        jdbc.execute("""
                create table if not exists user_role_assignments (
                    id bigint generated by default as identity primary key,
                    user_id bigint not null,
                    role_id bigint not null
                )
                """);
        jdbc.execute("create unique index if not exists uk_user_role_assignment on user_role_assignments(user_id,role_id)");
        jdbc.execute("create index if not exists idx_user_role_user on user_role_assignments(user_id)");
        jdbc.execute("create index if not exists idx_user_role_role on user_role_assignments(role_id)");
        if (columnExists("user_accounts", "access_role_id")) {
            jdbc.update("""
                    insert into user_role_assignments(user_id,role_id)
                    select u.id,u.access_role_id
                      from user_accounts u
                     where u.access_role_id is not null
                       and not exists (
                           select 1 from user_role_assignments ura
                            where ura.user_id=u.id and ura.role_id=u.access_role_id
                       )
                    """);
        }
    }

    private void normalizeAssetSource() {
        if (!columnExists("assets", "source_system")) return;
        jdbc.update("update assets set source_system = 'LOCAL' where source_system is null or trim(source_system) = ''");
        jdbc.execute("alter table assets alter column source_system set default 'LOCAL'");
        jdbc.execute("alter table assets alter column source_system set not null");
        if (columnExists("assets", "cmdb_item_id")) {
            jdbc.execute("create unique index if not exists uk_asset_cmdb_item on assets(cmdb_item_id)");
        }
    }

    private void normalizeTaskNumbers() {
        if (!columnExists("remediation_tasks", "task_no")) return;
        jdbc.update("""
                update remediation_tasks
                   set task_no = 'RMD-' || lpad(cast(id as varchar), 6, '0')
                 where task_no is null
                    or task_no not like 'RMD-______'
                """);
    }

    private void reconcileOperationalAssetReferences() {
        if (!tableExists("remediation_tasks") || !tableExists("findings") || !tableExists("assets")) return;
        jdbc.update("""
                update remediation_tasks t
                   set asset_id = (select f.asset_id from findings f where f.id = t.finding_id),
                       owner_id = (select a.owner_id from findings f join assets a on a.id = f.asset_id where f.id = t.finding_id),
                       owner_name = (select a.owner_name from findings f join assets a on a.id = f.asset_id where f.id = t.finding_id),
                       updated_at = current_timestamp
                 where exists (select 1 from findings f join assets a on a.id = f.asset_id where f.id = t.finding_id)
                """);
        if (tableExists("security_incidents")) {
            jdbc.update("""
                    update security_incidents i
                       set asset_id = (select f.asset_id from findings f where f.id = i.finding_id),
                           owner_id = (select a.owner_id from findings f join assets a on a.id = f.asset_id where f.id = i.finding_id),
                           owner_name = (select a.owner_name from findings f join assets a on a.id = f.asset_id where f.id = i.finding_id),
                           updated_at = current_timestamp
                     where exists (select 1 from findings f join assets a on a.id = f.asset_id where f.id = i.finding_id)
                    """);
        }
    }

    private void upgradeSeverityConstraint() {
        String name = "vulnerability_definitions_severity_check";
        if (!constraintExists("vulnerability_definitions", name)) return;
        String clause = jdbc.queryForObject("""
                select cc.check_clause
                from information_schema.check_constraints cc
                join information_schema.table_constraints tc
                  on lower(tc.constraint_name) = lower(cc.constraint_name)
                 and tc.constraint_schema = cc.constraint_schema
                where lower(tc.table_name) = 'vulnerability_definitions'
                  and lower(tc.constraint_name) = lower(?)
                """, String.class, name);
        if (clause != null && clause.toUpperCase().contains("UNKNOWN")) return;
        jdbc.execute("alter table vulnerability_definitions drop constraint " + name);
        jdbc.execute("""
                alter table vulnerability_definitions
                add constraint vulnerability_definitions_severity_check
                check (severity in ('CRITICAL','HIGH','MEDIUM','LOW','UNKNOWN'))
                """);
        log.info("Upgraded vulnerability severity constraint to support unscored CVEs");
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

    private boolean constraintExists(String table, String constraint) {
        Integer count = jdbc.queryForObject("""
                select count(*) from information_schema.table_constraints
                where lower(table_name) = lower(?)
                  and lower(constraint_name) = lower(?)
                """, Integer.class, table, constraint);
        return count != null && count > 0;
    }
}
