package com.gazellio.platform.service;
import com.gazellio.platform.dto.ApiDtos.SettingsUpdateRequest;
import com.gazellio.platform.model.SystemSetting;
import com.gazellio.platform.repository.SystemSettingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.scheduling.support.CronExpression;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;
@Service @RequiredArgsConstructor public class SettingsService {
 private static final Set<String> ALLOWED=Set.of("scanPolicyProd","scanPolicyTest","maintenanceWindow","autoRollbackThreshold",
         "batchSize","batchConcurrency","slaP1Days","slaP2Days","slaP3Days","slaP4Days","auditRetentionMonths",
         "slaWarningHours","slaEscalationHours","riskFormulaVersion","riskUnscoredBase","riskCriticalityWeight",
         "riskKevWeight","riskExposureWeight","riskProductionWeight","batchMaxRetries","batchObservationMinutes");
 private final SystemSettingRepository repo; private final AuditService audit; private final CurrentUserService currentUser;
 public Map<String,String> get(){return repo.findAll().stream().collect(Collectors.toMap(SystemSetting::getSettingKey,x->Optional.ofNullable(x.getSettingValue()).orElse(""),(a,b)->b,LinkedHashMap::new));}
 @Transactional public Map<String,String> update(SettingsUpdateRequest req){if(req.values()!=null)req.values().forEach((k,v)->{if(!ALLOWED.contains(k))return;validate(k,v);SystemSetting s=repo.findById(k).orElseGet(()->SystemSetting.builder().settingKey(k).build());s.setSettingValue(v==null?"":v.trim());s.setUpdatedAt(Instant.now());repo.save(s);});audit.log("SETTINGS","system","UPDATE","系统运行参数已更新","Runtime settings updated",currentUser.name());return get();}
 public String value(String key,String fallback){return repo.findById(key).map(SystemSetting::getSettingValue).filter(v->v!=null&&!v.isBlank()).orElse(fallback);}
 public int intValue(String key,int fallback,int min,int max){try{return Math.max(min,Math.min(max,Integer.parseInt(value(key,String.valueOf(fallback)))));}catch(Exception ignored){return fallback;}}
 public double doubleValue(String key,double fallback,double min,double max){try{return Math.max(min,Math.min(max,Double.parseDouble(value(key,String.valueOf(fallback)))));}catch(Exception ignored){return fallback;}}
 private void validate(String key,String value){
  if(value==null)return;
  try{
   if(key.startsWith("scanPolicy")){CronExpression.parse(value);return;}
   if(Set.of("autoRollbackThreshold","batchSize","batchConcurrency","slaP1Days","slaP2Days","slaP3Days","slaP4Days","auditRetentionMonths","slaWarningHours","slaEscalationHours","batchMaxRetries","batchObservationMinutes").contains(key)){
    int n=Integer.parseInt(value);int min="auditRetentionMonths".equals(key)?12:1;
    int max=switch(key){case "autoRollbackThreshold"->100;case "batchSize"->500;case "batchConcurrency"->200;case "batchMaxRetries"->10;case "batchObservationMinutes"->1440;case "auditRetentionMonths"->120;default->3650;};
    if(n<min||n>max)throw new IllegalArgumentException();
   }
   if(key.startsWith("risk")&&!"riskFormulaVersion".equals(key)){
    double n=Double.parseDouble(value);if(n<0||n>10)throw new IllegalArgumentException();
   }
  }catch(Exception e){throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST,"Invalid setting: "+key);}
 }
}
