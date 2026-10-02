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
import java.time.temporal.ChronoUnit;
import java.util.function.Supplier;
@Service @RequiredArgsConstructor
public class AuditService {
 static final String HASH_VERSION="SHA256_V2";
 private final AuditEventRepository repo;
 public synchronized void log(String type,Object id,String action,String zh,String en,String actor){
  HttpServletRequest request=currentRequest();
  String previous=repo.findTopByOrderByIdDesc().map(AuditEvent::getEventHash).orElse(null);
  // PostgreSQL stores timestamptz with microsecond precision. Hash exactly that value so a
  // persisted event produces the same digest when it is loaded and verified later.
  Instant created=Instant.now().truncatedTo(ChronoUnit.MICROS);
  AuditEvent event=AuditEvent.builder().entityType(type).entityId(String.valueOf(id)).action(action)
          .messageZh(zh).messageEn(en).actor(actor)
          .sourceIp(request==null?"SYSTEM":sourceIp(request))
          .userAgent(request==null?"ANOWX background service":request.getHeader("User-Agent"))
          .previousHash(previous).hashVersion(HASH_VERSION).createdAt(created).build();
  event.setEventHash(hash(event));repo.save(event);
 }
 synchronized <T> T withChainLock(Supplier<T> operation){return operation.get();}
 String hash(AuditEvent event){return sha256(String.join("|",value(event.getHashVersion()),value(event.getPreviousHash()),value(event.getEntityType()),
         value(event.getEntityId()),value(event.getAction()),value(event.getMessageZh()),value(event.getMessageEn()),
         value(event.getActor()),value(event.getSourceIp()),value(event.getUserAgent()),value(normalize(event.getCreatedAt()))));}
 private String sha256(String value){try{byte[] digest=MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));return java.util.HexFormat.of().formatHex(digest);}catch(Exception e){throw new IllegalStateException("Unable to hash audit event",e);}}
 private String value(Object value){return value==null?"":String.valueOf(value);}
 private Instant normalize(Instant value){return value==null?null:value.truncatedTo(ChronoUnit.MICROS);}
 private HttpServletRequest currentRequest(){
  return RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs?attrs.getRequest():null;
 }
 private String sourceIp(HttpServletRequest request){
  String forwarded=request.getHeader("X-Forwarded-For");
  return forwarded!=null&&!forwarded.isBlank()?forwarded.split(",")[0].trim():request.getRemoteAddr();
 }
}
