package com.gazellio.platform.service;
import com.gazellio.platform.model.AuditEvent;
import com.gazellio.platform.repository.AuditEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
@Service @RequiredArgsConstructor
public class AuditService {
 private final AuditEventRepository repo;
 public synchronized void log(String type,Object id,String action,String zh,String en,String actor){
  HttpServletRequest request=currentRequest();
  String previous=repo.findTopByOrderByIdDesc().map(AuditEvent::getEventHash).orElse(null);
  Instant created=Instant.now();
  AuditEvent event=AuditEvent.builder().entityType(type).entityId(String.valueOf(id)).action(action)
          .messageZh(zh).messageEn(en).actor(actor)
          .sourceIp(request==null?"SYSTEM":sourceIp(request))
          .userAgent(request==null?"Gazellio background service":request.getHeader("User-Agent"))
          .previousHash(previous).createdAt(created).build();
  event.setEventHash(hash(event));repo.save(event);
 }
 String hash(AuditEvent event){return sha256(String.join("|",value(event.getPreviousHash()),value(event.getEntityType()),
         value(event.getEntityId()),value(event.getAction()),value(event.getMessageZh()),value(event.getMessageEn()),
         value(event.getActor()),value(event.getSourceIp()),value(event.getUserAgent()),value(event.getCreatedAt())));}
 private String sha256(String value){try{byte[] digest=MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));return java.util.HexFormat.of().formatHex(digest);}catch(Exception e){throw new IllegalStateException("Unable to hash audit event",e);}}
 private String value(Object value){return value==null?"":String.valueOf(value);}
 private HttpServletRequest currentRequest(){
  return RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs?attrs.getRequest():null;
 }
 private String sourceIp(HttpServletRequest request){
  String forwarded=request.getHeader("X-Forwarded-For");
  return forwarded!=null&&!forwarded.isBlank()?forwarded.split(",")[0].trim():request.getRemoteAddr();
 }
}
