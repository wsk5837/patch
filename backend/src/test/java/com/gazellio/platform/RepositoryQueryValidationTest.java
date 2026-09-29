package com.gazellio.platform;

import com.gazellio.platform.model.Enums.FindingStatus;
import com.gazellio.platform.model.Enums.Severity;
import com.gazellio.platform.repository.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:gazellio;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "app.seed-demo-data=false",
        "app.cisa-kev-sync-on-startup=false",
        "spring.task.scheduling.enabled=false"
})
@ActiveProfiles("test")
class RepositoryQueryValidationTest {
    private static final List<FindingStatus> CLOSED =
            List.of(FindingStatus.RESOLVED, FindingStatus.FALSE_POSITIVE, FindingStatus.EXEMPTED);

    @Autowired FindingRepository findings;
    @Autowired PatchCveRepository patchCves;
    @Autowired ApprovalStepRepository approvalSteps;
    @Autowired OrchestrationTemplateStepRepository templateSteps;
    @Autowired OrchestrationRunStepRepository runSteps;

    @Test
    void optimizedQueriesAreValid() {
        assertThatCode(() -> {
            findings.findTop6ByStatusNotInOrderByRiskScoreDescLastSeenAtDesc(CLOSED);
            findings.countOpenByAsset(CLOSED);
            findings.countAffectedByCve(CLOSED);
            findings.countOpenBySeverity(CLOSED);
            findings.countOpenByEnvironment(CLOSED);
            findings.countNonCompliantAssets(CLOSED, List.of(Severity.CRITICAL, Severity.HIGH));
            patchCves.findByCveIdIn(List.of("CVE-TEST"));
            patchCves.findByPatchIdIn(List.of(-1L));
            patchCves.countAffectedAssets(List.of(-1L), CLOSED);
            approvalSteps.findByApprovalIdInOrderByApprovalIdAscStepOrderAsc(List.of(-1L));
            templateSteps.findByTemplateIdInOrderByTemplateIdAscStepOrderAsc(List.of(-1L));
            runSteps.findByRunIdInOrderByRunIdAscStepOrderAsc(List.of(-1L));
        }).doesNotThrowAnyException();
    }
}
