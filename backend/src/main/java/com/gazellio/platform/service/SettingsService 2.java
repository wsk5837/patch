package com.gazellio.platform.service;
import com.gazellio.platform.dto.ApiDtos.SettingsUpdateRequest;
import com.gazellio.platform.model.SystemSetting;
import com.gazellio.platform.repository.SystemSettingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;
@Service @RequiredArgsConstructor public class SettingsService {
 private final SystemSettingRepository repo; private final AuditService audit; private final CurrentUserService currentUser;
 public Map<String,String> get(){return repo.findAll().stream().collect(Collectors.toMap(SystemSetting::getSettingKey,x->Optional.ofNullable(x.getSettingValue()).orElse(""),(a,b)->b,LinkedHashMap::new));}
 @Transactional public Map<String,String> update(SettingsUpdateRequest req){if(req.values()!=null)req.values().forEach((k,v)->{SystemSetting s=repo.findById(k).orElseGet(()->SystemSetting.builder().settingKey(k).build());s.setSettingValue(v);s.setUpdatedAt(Instant.now());repo.save(s);});audit.log("SETTINGS","system","UPDATE","系统设置已更新","System settings updated",currentUser.name());return get();}
}
