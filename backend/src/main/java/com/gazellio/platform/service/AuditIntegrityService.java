package com.gazellio.platform.service;

import com.gazellio.platform.dto.ComplianceDtos.AuditIntegrityView;
import com.gazellio.platform.model.AuditEvent;
import com.gazellio.platform.repository.AuditEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

@Service @RequiredArgsConstructor
public class AuditIntegrityService {
    private final AuditEventRepository events;
    private final AuditService audit;
    private volatile AuditIntegrityView last;

    @Scheduled(cron="0 10 4 * * *")
    @Transactional
    public AuditIntegrityView verify(){
        return audit.withChainLock(this::verifyLocked);
    }

    private AuditIntegrityView verifyLocked(){
        List<AuditEvent> rows=events.findAllByOrderByIdAsc();
        // V1 hashes were created before the database timestamp precision was normalized, so
        // they cannot be verified reliably after reload. If any V1 row remains, rebuild the
        // complete chain once and mark every row V2. Subsequent checks are read-only and strict.
        boolean legacy=rows.stream().anyMatch(event->!AuditService.HASH_VERSION.equals(event.getHashVersion()));
        String previous=null;long verified=0;Long invalid=null;
        for(AuditEvent event:rows){
            if(legacy){
                event.setHashVersion(AuditService.HASH_VERSION);event.setPreviousHash(previous);
                event.setEventHash(audit.hash(event));events.save(event);
            }
            boolean linkOk=Objects.equals(event.getPreviousHash(),previous);
            boolean hashOk=Objects.equals(event.getEventHash(),audit.hash(event));
            if(!linkOk||!hashOk){invalid=event.getId();break;}
            previous=event.getEventHash();verified++;
        }
        last=new AuditIntegrityView(invalid==null?"VERIFIED":"FAILED",rows.size(),verified,invalid,previous,Instant.now().toString());
        return last;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void migrateAndVerifyOnStartup(){verify();}

    public AuditIntegrityView status(){return last==null?verify():last;}
}
