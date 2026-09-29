package com.gazellio.platform;

import com.gazellio.platform.config.SchemaCompatibilityMigration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:gazellio-migration;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "app.seed-demo-data=false",
        "app.cisa-kev-sync-on-startup=false",
        "spring.task.scheduling.enabled=false"
})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class SchemaCompatibilityMigrationTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired SchemaCompatibilityMigration migration;

    @Test
    void upgradesLegacyRetestTargetConstraint() {
        jdbc.execute("delete from deployment_targets");
        jdbc.execute("alter table deployment_targets alter column deployment_id set not null");
        migration.migrate();

        String nullable = jdbc.queryForObject("""
                select is_nullable from information_schema.columns
                where lower(table_name) = 'deployment_targets'
                  and lower(column_name) = 'deployment_id'
                """, String.class);
        assertEquals("YES", nullable);
    }
}
