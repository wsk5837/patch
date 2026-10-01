package com.gazellio.platform.config;

import com.gazellio.platform.service.CmdbSyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Component @RequiredArgsConstructor @Slf4j
public class CmdbStartupSync {
    private final CmdbProperties properties;
    private final CmdbSyncService service;
    @Async @EventListener(ApplicationReadyEvent.class)
    public void synchronizeAfterStartup(){
        if(!properties.configured()||!properties.isSyncOnStartup())return;
        try{service.synchronize();}catch(Exception ex){log.warn("Initial read-only CMDB sync failed: {}",ex.getMessage());}
    }
}
