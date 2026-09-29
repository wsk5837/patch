package com.gazellio.platform.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.gazellio.platform.model.VulnerabilityDefinition;
import com.gazellio.platform.repository.VulnerabilityDefinitionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.annotation.Async;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;

import java.time.*;
import java.util.Iterator;
import static com.gazellio.platform.model.Enums.Severity;

@Service @RequiredArgsConstructor
public class ThreatIntelSyncService {
    private final VulnerabilityDefinitionRepository repo;
    private final RestClient.Builder rest;
    @Value("${app.cisa-kev-url}") private String cisaUrl;

    @Async
    @EventListener(ApplicationReadyEvent.class)
    public void syncOnStartup(){
        try { syncCisaKev(); } catch(Exception ignored) {}
    }

    @Scheduled(cron = "${app.cisa-kev-sync-cron:0 15 3 * * *}")
    public void scheduledSync(){
        try { syncCisaKev(); } catch(Exception ignored) {}
    }

    @Transactional
    public int syncCisaKev(){
        JsonNode root=rest.build().get().uri(cisaUrl).retrieve().body(JsonNode.class);
        if(root==null||!root.has("vulnerabilities")) return 0;
        int n=0; Iterator<JsonNode> it=root.get("vulnerabilities").elements();
        while(it.hasNext()){
            JsonNode x=it.next(); String cve=text(x,"cveID"); if(cve==null)continue;
            VulnerabilityDefinition v=repo.findById(cve).orElseGet(()->VulnerabilityDefinition.builder().cveId(cve).cvss(null).severity(Severity.HIGH).patchAvailable(false).build());
            String vendor=text(x,"vendorProject"),product=text(x,"product"),name=text(x,"vulnerabilityName");
            v.setVendor(vendor); v.setProduct(product); v.setKev(true); v.setRansomwareKnown("Known".equalsIgnoreCase(text(x,"knownRansomwareCampaignUse")));
            v.setTitleEn(name==null?cve:name); v.setTitleZh((vendor==null?"":vendor+" ")+(product==null?"":product+" ")+"已知利用漏洞（"+cve+"）");
            v.setDescriptionEn(text(x,"shortDescription")); v.setDescriptionZh("该漏洞已被列入 CISA 已知被利用漏洞目录，需结合资产影响范围和厂商修复方案优先处置。");
            v.setReferenceUrl("https://www.cisa.gov/known-exploited-vulnerabilities-catalog");
            try{String d=text(x,"dateAdded");if(d!=null)v.setPublishedDate(LocalDate.parse(d));}catch(Exception ignored){}
            try{String d=text(x,"dueDate");if(d!=null)v.setKevDueDate(LocalDate.parse(d));}catch(Exception ignored){}
            v.setUpdatedAt(Instant.now()); repo.save(v); n++;
        }
        return n;
    }
    private static String text(JsonNode n,String k){return n.hasNonNull(k)?n.get(k).asText():null;}
}
