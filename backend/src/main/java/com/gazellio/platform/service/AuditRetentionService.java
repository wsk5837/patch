package com.gazellio.platform.service;

import com.gazellio.platform.repository.AuditEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Service
@RequiredArgsConstructor
public class AuditRetentionService {
    private final AuditEventRepository events;
    private final SettingsService settings;
    private final AuditService audit;

    @Scheduled(cron = "0 20 4 * * *")
    @Transactional
    public void enforceRetention(){
        int months=settings.intValue("auditRetentionMonths",12,12,120);
        long removed=events.deleteByCreatedAtBefore(Instant.now().minus(months*30L,ChronoUnit.DAYS));
        if(removed>0)audit.log("AUDIT_RETENTION","system","PURGE","审计留存策略清理 "+removed+" 条到期记录",
                "Audit retention removed "+removed+" expired records","Gazellio Scheduler");
    }
}
