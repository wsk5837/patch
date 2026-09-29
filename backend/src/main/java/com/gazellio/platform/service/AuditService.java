package com.gazellio.platform.service;
import com.gazellio.platform.model.AuditEvent;
import com.gazellio.platform.repository.AuditEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
@Service @RequiredArgsConstructor
public class AuditService {
 private final AuditEventRepository repo;
 public void log(String type,Object id,String action,String zh,String en,String actor){ repo.save(AuditEvent.builder().entityType(type).entityId(String.valueOf(id)).action(action).messageZh(zh).messageEn(en).actor(actor).build()); }
}
