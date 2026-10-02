package com.gazellio.platform.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.gazellio.platform.dto.ApiDtos.CisaKevSyncResult;
import com.gazellio.platform.model.VulnerabilityDefinition;
import com.gazellio.platform.repository.PatchCveRepository;
import com.gazellio.platform.repository.VulnerabilityDefinitionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.annotation.Async;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.*;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import static com.gazellio.platform.model.Enums.Severity;

@Service @RequiredArgsConstructor @Slf4j
public class ThreatIntelSyncService {
    private final VulnerabilityDefinitionRepository repo;
    private final PatchCveRepository patchCves;
    private final RestClient.Builder rest;
    @Value("${app.cisa-kev-url}") private String cisaUrl;
    @Value("${app.cisa-kev-fallback-url}") private String fallbackUrl;
    @Value("${app.cisa-kev-sync-on-startup:false}") private boolean syncOnStartupEnabled;

    @Async
    @EventListener(ApplicationReadyEvent.class)
    public void syncOnStartup(){
        if (!syncOnStartupEnabled) return;
        try { syncCisaKev(); } catch(Exception error) { log.warn("CISA KEV startup sync failed", error); }
    }

    @Scheduled(cron = "${app.cisa-kev-sync-cron:0 15 3 * * *}")
    public void scheduledSync(){
        try { syncCisaKev(); } catch(Exception error) { log.warn("CISA KEV scheduled sync failed", error); }
    }

    @Transactional
    public synchronized CisaKevSyncResult syncCisaKev(){
        FeedResponse feed=fetchCatalog();
        JsonNode root=feed.body();
        if(root==null||!root.path("vulnerabilities").isArray()) {
            throw new IllegalStateException("CISA KEV catalog response is missing the vulnerabilities array");
        }
        List<JsonNode> entries=new ArrayList<>();
        root.get("vulnerabilities").elements().forEachRemaining(entries::add);
        Set<String> supportedCves=patchCves.findAll().stream().map(x->x.getCveId().toUpperCase(Locale.ROOT)).collect(Collectors.toSet());
        Set<String> cves=entries.stream().map(x->text(x,"cveID")).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<String,VulnerabilityDefinition> existing=repo.findAllById(cves).stream().collect(Collectors.toMap(VulnerabilityDefinition::getCveId,Function.identity()));
        List<VulnerabilityDefinition> updates=new ArrayList<>(entries.size());
        int created=0,changed=0;
        for(JsonNode x:entries){
            String cve=text(x,"cveID"); if(cve==null)continue;
            boolean isNew=!existing.containsKey(cve);
            VulnerabilityDefinition v=existing.getOrDefault(cve,VulnerabilityDefinition.builder().cveId(cve).cvss(null).severity(Severity.UNKNOWN).patchAvailable(false).build());
            if(v.getCvss()==null)v.setSeverity(Severity.UNKNOWN);
            String vendor=limit(text(x,"vendorProject"),160),product=limit(text(x,"product"),160),name=limit(text(x,"vulnerabilityName"),500);
            v.setVendor(vendor); v.setProduct(product); v.setKev(true); v.setRansomwareKnown("Known".equalsIgnoreCase(text(x,"knownRansomwareCampaignUse")));
            v.setTitleEn(name==null?cve:name); v.setTitleZh(limit((vendor==null?"":vendor+" ")+(product==null?"":product+" ")+"已知利用漏洞（"+cve+"）",500));
            v.setDescriptionEn(text(x,"shortDescription")); v.setDescriptionZh("该漏洞已被列入 CISA 已知被利用漏洞目录，需结合资产影响范围和厂商修复方案优先处置。");
            v.setPatchAvailable(supportedCves.contains(cve.toUpperCase(Locale.ROOT)));
            v.setReferenceUrl("https://www.cisa.gov/known-exploited-vulnerabilities-catalog");
            try{String d=text(x,"dateAdded");if(d!=null)v.setPublishedDate(LocalDate.parse(d));}catch(Exception ignored){}
            try{String d=text(x,"dueDate");if(d!=null)v.setKevDueDate(LocalDate.parse(d));}catch(Exception ignored){}
            v.setUpdatedAt(Instant.now()); updates.add(v);
            if(isNew)created++;else changed++;
        }
        repo.saveAll(updates);
        int catalogTotal=root.path("count").asInt(entries.size());
        int matched=(int)entries.stream().map(x->text(x,"cveID")).filter(Objects::nonNull)
                .filter(cve->supportedCves.contains(cve.toUpperCase(Locale.ROOT))).count();
        CisaKevSyncResult result=new CisaKevSyncResult(text(root,"catalogVersion"),text(root,"dateReleased"),catalogTotal,
                matched,created,changed,Math.max(0,catalogTotal-matched),Instant.now().toString(),feed.source());
        log.info("CISA KEV sync completed: source={}, catalogTotal={}, matched={}, created={}, updated={}",feed.source(),catalogTotal,matched,created,changed);
        return result;
    }

    private FeedResponse fetchCatalog(){
        try{return new FeedResponse(fetch(cisaUrl),"CISA");}
        catch(RestClientException primary){
            if(fallbackUrl==null||fallbackUrl.isBlank()||fallbackUrl.equals(cisaUrl))throw primary;
            log.warn("CISA feed unavailable; using the official cisagov/kev-data mirror: {}",primary.getMessage());
            try{return new FeedResponse(fetch(fallbackUrl),"CISA GitHub mirror");}
            catch(RestClientException secondary){
                secondary.addSuppressed(primary);
                throw new IllegalStateException("Unable to download the CISA KEV catalog from the primary feed or official mirror",secondary);
            }
        }
    }

    private JsonNode fetch(String url){
        return rest.build().get().uri(url)
                .header(HttpHeaders.ACCEPT,MediaType.APPLICATION_JSON_VALUE)
                .header(HttpHeaders.USER_AGENT,"ANOWX/1.0 (+https://github.com/wsk5837/patch)")
                .retrieve().body(JsonNode.class);
    }

    private record FeedResponse(JsonNode body,String source){}
    private static String text(JsonNode n,String k){return n.hasNonNull(k)?n.get(k).asText():null;}
    private static String limit(String value,int max){return value!=null&&value.length()>max?value.substring(0,max):value;}
}
