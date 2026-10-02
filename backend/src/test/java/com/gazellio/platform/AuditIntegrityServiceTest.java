package com.gazellio.platform;

import com.gazellio.platform.model.AuditEvent;
import com.gazellio.platform.repository.AuditEventRepository;
import com.gazellio.platform.service.AuditIntegrityService;
import com.gazellio.platform.service.AuditService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import java.time.Instant;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties={
        "spring.datasource.url=jdbc:h2:mem:gazellio-audit;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop","app.seed-demo-data=false",
        "app.cisa-kev-sync-on-startup=false","spring.task.scheduling.enabled=false"
})
@DirtiesContext(classMode=DirtiesContext.ClassMode.AFTER_CLASS)
class AuditIntegrityServiceTest {
    @Autowired AuditEventRepository events;
    @Autowired AuditService audit;
    @Autowired AuditIntegrityService integrity;

    @Test void migratesLegacyRowsAndDetectsLaterModification(){
        events.save(AuditEvent.builder().entityType("TEST").entityId("1").action("CREATE")
                .messageZh("创建").messageEn("Created").actor("tester").createdAt(Instant.now()).build());
        var migrated=integrity.verify();
        assertEquals("VERIFIED",migrated.status());
        assertNotNull(events.findAll().getFirst().getHashVersion());

        audit.log("TEST",2,"UPDATE","更新","Updated","tester");
        assertEquals("VERIFIED",integrity.verify().status());

        AuditEvent changed=events.findAllByOrderByIdAsc().getFirst();
        changed.setMessageZh("被修改");events.saveAndFlush(changed);
        var failed=integrity.verify();
        assertEquals("FAILED",failed.status());
        assertEquals(changed.getId(),failed.firstInvalidEventId());
    }
}
