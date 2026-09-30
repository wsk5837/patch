package com.gazellio.platform.service;
import com.gazellio.platform.model.AuditEvent;
import com.gazellio.platform.repository.AuditEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import jakarta.servlet.http.HttpServletRequest;
@Service @RequiredArgsConstructor
public class AuditService {
 private final AuditEventRepository repo;
 public void log(String type,Object id,String action,String zh,String en,String actor){
  HttpServletRequest request=currentRequest();
  repo.save(AuditEvent.builder().entityType(type).entityId(String.valueOf(id)).action(action)
          .messageZh(zh).messageEn(en).actor(actor)
          .sourceIp(request==null?"SYSTEM":sourceIp(request))
          .userAgent(request==null?"Gazellio background service":request.getHeader("User-Agent")).build());
 }
 private HttpServletRequest currentRequest(){
  return RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs?attrs.getRequest():null;
 }
 private String sourceIp(HttpServletRequest request){
  String forwarded=request.getHeader("X-Forwarded-For");
  return forwarded!=null&&!forwarded.isBlank()?forwarded.split(",")[0].trim():request.getRemoteAddr();
 }
}
