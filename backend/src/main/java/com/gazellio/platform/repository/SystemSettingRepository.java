package com.gazellio.platform.repository;
import com.gazellio.platform.model.SystemSetting;
import org.springframework.data.jpa.repository.JpaRepository;
public interface SystemSettingRepository extends JpaRepository<SystemSetting, String> {}
