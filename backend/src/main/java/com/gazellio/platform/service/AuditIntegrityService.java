package com.gazellio.platform.service;

import com.gazellio.platform.dto.ComplianceDtos.AuditIntegrityView;
import com.gazellio.platform.model.AuditEvent;
import com.gazellio.platform.repository.AuditEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
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
        List<AuditEvent> rows=events.findAllByOrderByIdAsc();
        String previous=rows.isEmpty()?null:rows.getFirst().getPreviousHash();long verified=0;Long invalid=null;
        for(AuditEvent event:rows){
            if(event.getEventHash()==null||event.getEventHash().isBlank()){
                event.setPreviousHash(previous);event.setEventHash(audit.hash(event));events.save(event);
            }
            boolean linkOk=Objects.equals(event.getPreviousHash(),previous);
            boolean hashOk=Objects.equals(event.getEventHash(),audit.hash(event));
            if(!linkOk||!hashOk){invalid=event.getId();break;}
            previous=event.getEventHash();verified++;
        }
        last=new AuditIntegrityView(invalid==null?"VERIFIED":"FAILED",rows.size(),verified,invalid,previous,Instant.now().toString());
        return last;
    }

    public AuditIntegrityView status(){return last==null?verify():last;}
}
