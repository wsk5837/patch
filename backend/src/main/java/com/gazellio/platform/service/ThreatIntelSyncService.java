package com.gazellio.platform.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.gazellio.platform.dto.ApiDtos.CisaKevSyncResult;
import com.gazellio.platform.dto.ApiDtos.ThreatIntelSourceSyncResult;
import com.gazellio.platform.dto.ApiDtos.ThreatIntelSyncResult;
import com.gazellio.platform.model.VulnerabilityDefinition;
import com.gazellio.platform.repository.PatchCveRepository;
import com.gazellio.platform.repository.VulnerabilityDefinitionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.gazellio.platform.model.Enums.Severity;

@Service @RequiredArgsConstructor @Slf4j
public class ThreatIntelSyncService {
    private static final String USER_AGENT="ANOWX/1.0 (+https://github.com/wsk5837/patch)";
    private final VulnerabilityDefinitionRepository repo;
    private final PatchCveRepository patchCves;
    private final RestClient.Builder rest;

    @Value("${app.cisa-kev-url}") private String cisaUrl;
    @Value("${app.cisa-kev-fallback-url}") private String fallbackUrl;
    @Value("${app.cisa-kev-sync-on-startup:false}") private boolean syncOnStartupEnabled;
    @Value("${app.nvd-url:https://services.nvd.nist.gov/rest/json/cves/2.0}") private String nvdUrl;
    @Value("${app.nvd-api-key:}") private String nvdApiKey;
    @Value("${app.nvd-lookback-days:120}") private int nvdLookbackDays;
    @Value("${app.nvd-max-records:4000}") private int nvdMaxRecords;
    @Value("${app.github-advisory-url:https://api.github.com/advisories}") private String githubAdvisoryUrl;
    @Value("${app.github-api-token:}") private String githubApiToken;
    @Value("${app.github-advisory-pages:3}") private int githubAdvisoryPages;

    @Async
    @EventListener(ApplicationReadyEvent.class)
    public void syncOnStartup(){
        if (!syncOnStartupEnabled) return;
        try { syncOfficialSources(); } catch(Exception error) { log.warn("Official vulnerability intelligence startup sync failed", error); }
    }

    @Scheduled(cron = "${app.cisa-kev-sync-cron:0 15 3 * * *}")
    public void scheduledSync(){
        try { syncOfficialSources(); } catch(Exception error) { log.warn("Official vulnerability intelligence scheduled sync failed", error); }
    }

    /** Synchronize independent official feeds. One unavailable provider does not discard successful providers. */
    public synchronized ThreatIntelSyncResult syncOfficialSources(){
        List<ThreatIntelSourceSyncResult> results=new ArrayList<>();
        try {
            CisaKevSyncResult c=syncCisaKev();
            results.add(new ThreatIntelSourceSyncResult(c.source(),c.catalogTotal(),c.catalogTotal(),c.created(),c.updated(),0,"SUCCESS",
                    "catalog="+nullSafe(c.catalogVersion())+", patchMappings="+c.matchedByPatchLibrary()));
        } catch(Exception error) { results.add(failed("CISA KEV",error)); }
        try { results.add(syncNvd()); } catch(Exception error) { results.add(failed("NVD",error)); }
        try { results.add(syncGithubAdvisories()); } catch(Exception error) { results.add(failed("GitHub Advisory Database",error)); }

        int received=results.stream().mapToInt(ThreatIntelSourceSyncResult::received).sum();
        int accepted=results.stream().mapToInt(ThreatIntelSourceSyncResult::accepted).sum();
        int created=results.stream().mapToInt(ThreatIntelSourceSyncResult::created).sum();
        int updated=results.stream().mapToInt(ThreatIntelSourceSyncResult::updated).sum();
        int skipped=results.stream().mapToInt(ThreatIntelSourceSyncResult::skipped).sum();
        return new ThreatIntelSyncResult(results,received,accepted,created,updated,skipped,repo.count(),Instant.now().toString());
    }

    @Transactional
    public CisaKevSyncResult syncCisaKev(){
        FeedResponse feed=fetchCatalog();
        JsonNode root=feed.body();
        if(root==null||!root.path("vulnerabilities").isArray()) {
            throw new IllegalStateException("CISA KEV catalog response is missing the vulnerabilities array");
        }
        List<JsonNode> entries=new ArrayList<>();
        root.get("vulnerabilities").elements().forEachRemaining(entries::add);
        Set<String> supportedCves=supportedCves();
        Set<String> cves=entries.stream().map(x->text(x,"cveID")).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<String,VulnerabilityDefinition> existing=repo.findAllById(cves).stream().collect(Collectors.toMap(VulnerabilityDefinition::getCveId,Function.identity()));
        List<VulnerabilityDefinition> updates=new ArrayList<>(entries.size());
        int created=0,changed=0;
        for(JsonNode x:entries){
            String cve=upperCve(text(x,"cveID")); if(cve==null)continue;
            boolean isNew=!existing.containsKey(cve);
            VulnerabilityDefinition v=existing.getOrDefault(cve,newDefinition(cve));
            if(v.getCvss()==null)v.setSeverity(Severity.UNKNOWN);
            String vendor=limit(text(x,"vendorProject"),160),product=limit(text(x,"product"),160),name=limit(text(x,"vulnerabilityName"),500);
            setIfPresent(v::setVendor,vendor); setIfPresent(v::setProduct,product); v.setKev(true);
            v.setRansomwareKnown("Known".equalsIgnoreCase(text(x,"knownRansomwareCampaignUse")));
            if(isBlank(v.getTitleEn()))v.setTitleEn(name==null?cve:name);
            if(isBlank(v.getTitleZh()))v.setTitleZh(limit((vendor==null?"":vendor+" ")+(product==null?"":product+" ")+"已知利用漏洞（"+cve+"）",500));
            setIfPresent(v::setDescriptionEn,text(x,"shortDescription"));
            if(isBlank(v.getDescriptionZh()))v.setDescriptionZh("该漏洞已被列入 CISA 已知被利用漏洞目录，需结合资产影响范围和厂商修复方案优先处置。");
            v.setPatchAvailable(supportedCves.contains(cve));
            v.setReferenceUrl("https://www.cisa.gov/known-exploited-vulnerabilities-catalog");
            v.setIntelligenceSources(mergeSource(v.getIntelligenceSources(),"CISA KEV"));
            try{String d=text(x,"dateAdded");if(d!=null)v.setPublishedDate(LocalDate.parse(d));}catch(Exception ignored){}
            try{String d=text(x,"dueDate");if(d!=null)v.setKevDueDate(LocalDate.parse(d));}catch(Exception ignored){}
            v.setLastAnalyzedAt(Instant.now()); v.setUpdatedAt(Instant.now()); updates.add(v);
            if(isNew)created++;else changed++;
        }
        repo.saveAll(updates);
        int catalogTotal=root.path("count").asInt(entries.size());
        int matched=(int)entries.stream().map(x->upperCve(text(x,"cveID"))).filter(Objects::nonNull).filter(supportedCves::contains).count();
        CisaKevSyncResult result=new CisaKevSyncResult(text(root,"catalogVersion"),text(root,"dateReleased"),catalogTotal,
                matched,created,changed,Math.max(0,catalogTotal-matched),Instant.now().toString(),feed.source());
        log.info("CISA KEV sync completed: source={}, catalogTotal={}, matched={}, created={}, updated={}",feed.source(),catalogTotal,matched,created,changed);
        return result;
    }

    public ThreatIntelSourceSyncResult syncNvd(){
        int pageSize=Math.min(2000,Math.max(100,Math.min(nvdMaxRecords,2000)));
        int startIndex=0,totalResults=Integer.MAX_VALUE,received=0,accepted=0,created=0,updated=0,skipped=0;
        OffsetDateTime end=OffsetDateTime.now(ZoneOffset.UTC);
        OffsetDateTime start=end.minusDays(Math.max(1,Math.min(nvdLookbackDays,120)));
        while(startIndex<totalResults&&received<nvdMaxRecords){
            int requestSize=Math.min(pageSize,nvdMaxRecords-received);
            URI uri=UriComponentsBuilder.fromUriString(nvdUrl)
                    .queryParam("pubStartDate",start.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME))
                    .queryParam("pubEndDate",end.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME))
                    .queryParam("startIndex",startIndex).queryParam("resultsPerPage",requestSize).build().encode().toUri();
            JsonNode root=rest.build().get().uri(uri).headers(headers->{
                headers.set(HttpHeaders.ACCEPT,MediaType.APPLICATION_JSON_VALUE); headers.set(HttpHeaders.USER_AGENT,USER_AGENT);
                if(!isBlank(nvdApiKey))headers.set("apiKey",nvdApiKey);
            }).retrieve().body(JsonNode.class);
            if(root==null||!root.path("vulnerabilities").isArray())throw new IllegalStateException("NVD response is missing vulnerabilities");
            totalResults=root.path("totalResults").asInt(0);
            List<Incoming> rows=new ArrayList<>();
            for(JsonNode wrapper:root.path("vulnerabilities")){
                received++;
                Incoming incoming=nvdIncoming(wrapper.path("cve"));
                if(incoming==null)skipped++;else {rows.add(incoming);accepted++;}
            }
            UpsertCount count=upsert(rows); created+=count.created();updated+=count.updated();
            int returned=root.path("resultsPerPage").asInt(rows.size());
            if(returned<=0)break;
            startIndex+=returned;
        }
        log.info("NVD sync completed: received={}, accepted={}, created={}, updated={}",received,accepted,created,updated);
        return new ThreatIntelSourceSyncResult("NVD",received,accepted,created,updated,skipped,"SUCCESS",
                "Recent "+Math.max(1,Math.min(nvdLookbackDays,120))+" days; official CVE API 2.0");
    }

    public ThreatIntelSourceSyncResult syncGithubAdvisories(){
        int received=0,accepted=0,created=0,updated=0,skipped=0;
        int pages=Math.max(1,Math.min(githubAdvisoryPages,10));
        for(int page=1;page<=pages;page++){
            URI uri=UriComponentsBuilder.fromUriString(githubAdvisoryUrl).queryParam("per_page",100)
                    .queryParam("page",page).queryParam("type","reviewed").queryParam("sort","published")
                    .queryParam("direction","desc").build().encode().toUri();
            JsonNode body=rest.build().get().uri(uri).headers(headers->{
                headers.set(HttpHeaders.ACCEPT,"application/vnd.github+json"); headers.set(HttpHeaders.USER_AGENT,USER_AGENT);
                headers.set("X-GitHub-Api-Version","2022-11-28");
                if(!isBlank(githubApiToken))headers.setBearerAuth(githubApiToken);
            }).retrieve().body(JsonNode.class);
            if(body==null||!body.isArray())throw new IllegalStateException("GitHub Advisory response is not an array");
            List<Incoming> rows=new ArrayList<>();
            for(JsonNode advisory:body){
                received++;
                Incoming incoming=githubIncoming(advisory);
                if(incoming==null)skipped++;else {rows.add(incoming);accepted++;}
            }
            UpsertCount count=upsert(rows);created+=count.created();updated+=count.updated();
            if(body.size()<100)break;
        }
        log.info("GitHub Advisory sync completed: received={}, accepted={}, created={}, updated={}",received,accepted,created,updated);
        return new ThreatIntelSourceSyncResult("GitHub Advisory Database",received,accepted,created,updated,skipped,"SUCCESS",
                "Reviewed public security advisories");
    }

    private Incoming nvdIncoming(JsonNode cve){
        String id=upperCve(text(cve,"id")); if(id==null)return null;
        String description=englishValue(cve.path("descriptions"));
        JsonNode metric=firstMetric(cve.path("metrics"));
        JsonNode data=metric==null?null:metric.path("cvssData");
        Double score=data!=null&&data.hasNonNull("baseScore")?data.path("baseScore").asDouble():null;
        String severity=metric==null?null:text(metric,"baseSeverity");
        String vector=data==null?null:text(data,"vectorString");
        String cwe=firstWeakness(cve.path("weaknesses"));
        String cpe=findFirstCpe(cve.path("configurations"));
        String[] productParts=parseCpe(cpe);
        LocalDate published=parseDate(text(cve,"published"));
        String title=description==null?id:limit(description,500);
        return new Incoming(id,title,description,productParts[0],productParts[1],score,severityFrom(score,severity),cwe,vector,published,
                "https://nvd.nist.gov/vuln/detail/"+id,null,null,"NVD");
    }

    private Incoming githubIncoming(JsonNode advisory){
        String id=upperCve(text(advisory,"cve_id")); if(id==null)return null;
        JsonNode vulnerability=advisory.path("vulnerabilities").isArray()&&advisory.path("vulnerabilities").size()>0?advisory.path("vulnerabilities").get(0):null;
        JsonNode pkg=vulnerability==null?null:vulnerability.path("package");
        String vendor=pkg==null?null:text(pkg,"ecosystem"),product=pkg==null?null:text(pkg,"name");
        JsonNode cvss=advisory.path("cvss");
        Double score=cvss.hasNonNull("score")?cvss.path("score").asDouble():null;
        String cwe=null;
        if(advisory.path("cwes").isArray()&&advisory.path("cwes").size()>0)cwe=text(advisory.path("cwes").get(0),"cwe_id");
        String affected=vulnerability==null?null:text(vulnerability,"vulnerable_version_range");
        String fixed=null;
        if(vulnerability!=null&&vulnerability.path("first_patched_version").isObject())fixed=text(vulnerability.path("first_patched_version"),"identifier");
        String ref=text(advisory,"html_url");
        if(ref==null)ref="https://github.com/advisories/"+text(advisory,"ghsa_id");
        return new Incoming(id,text(advisory,"summary"),text(advisory,"description"),vendor,product,score,
                severityFrom(score,text(advisory,"severity")),cwe,text(cvss,"vector_string"),parseDate(text(advisory,"published_at")),
                ref,affected,fixed,"GitHub Advisory Database");
    }

    private UpsertCount upsert(List<Incoming> incomingRows){
        if(incomingRows.isEmpty())return new UpsertCount(0,0);
        Map<String,Incoming> unique=new LinkedHashMap<>(); incomingRows.forEach(row->unique.put(row.cveId(),row));
        Map<String,VulnerabilityDefinition> existing=repo.findAllById(unique.keySet()).stream()
                .collect(Collectors.toMap(VulnerabilityDefinition::getCveId,Function.identity()));
        Set<String> supported=supportedCves();
        List<VulnerabilityDefinition> saved=new ArrayList<>(unique.size());
        int created=0,updated=0;
        for(Incoming row:unique.values()){
            boolean isNew=!existing.containsKey(row.cveId());
            VulnerabilityDefinition v=existing.getOrDefault(row.cveId(),newDefinition(row.cveId()));
            if(isNew)created++;else updated++;
            setIfPresent(v::setVendor,limit(row.vendor(),160)); setIfPresent(v::setProduct,limit(row.product(),160));
            if(isNew||isBlank(v.getTitleEn()))v.setTitleEn(limit(or(row.title(),row.cveId()),500));
            if(isNew||isBlank(v.getTitleZh()))v.setTitleZh(limit(row.cveId()+" · "+or(row.product(),"安全漏洞"),500));
            if(!isBlank(row.description()))v.setDescriptionEn(row.description());
            if(isBlank(v.getDescriptionZh()))v.setDescriptionZh("由 "+row.source()+" 官方数据源同步，受影响范围、修复版本与处置要求以原始公告及厂商公告为准。");
            if(row.cvss()!=null){v.setCvss(row.cvss());v.setSeverity(row.severity());}
            else if(v.getCvss()==null)v.setSeverity(Severity.UNKNOWN);
            setIfPresent(v::setCweId,limit(row.cwe(),40)); setIfPresent(v::setCvssVector,limit(row.vector(),220));
            if(row.published()!=null)v.setPublishedDate(row.published());
            setIfPresent(v::setReferenceUrl,limit(row.reference(),800));
            if(!isBlank(row.affectedRange())){v.setAffectedVersionRangeEn(row.affectedRange());v.setAffectedVersionRangeZh(row.affectedRange());}
            setIfPresent(v::setFixedVersion,limit(row.fixedVersion(),160));
            v.setPatchAvailable(supported.contains(row.cveId()));
            v.setIntelligenceSources(mergeSource(v.getIntelligenceSources(),row.source()));
            v.setLastAnalyzedAt(Instant.now());v.setUpdatedAt(Instant.now());saved.add(v);
        }
        repo.saveAll(saved); return new UpsertCount(created,updated);
    }

    private VulnerabilityDefinition newDefinition(String cve){
        return VulnerabilityDefinition.builder().cveId(cve).titleZh(cve).titleEn(cve).cvss(null)
                .severity(Severity.UNKNOWN).patchAvailable(false).kev(false).ransomwareKnown(false).updatedAt(Instant.now()).build();
    }

    private FeedResponse fetchCatalog(){
        try{return new FeedResponse(fetch(cisaUrl),"CISA KEV");}
        catch(RestClientException primary){
            if(fallbackUrl==null||fallbackUrl.isBlank()||fallbackUrl.equals(cisaUrl))throw primary;
            log.warn("CISA feed unavailable; using the official cisagov/kev-data mirror: {}",primary.getMessage());
            try{return new FeedResponse(fetch(fallbackUrl),"CISA KEV GitHub mirror");}
            catch(RestClientException secondary){secondary.addSuppressed(primary);throw new IllegalStateException("Unable to download the CISA KEV catalog from the primary feed or official mirror",secondary);}
        }
    }

    private JsonNode fetch(String url){
        return rest.build().get().uri(url).header(HttpHeaders.ACCEPT,MediaType.APPLICATION_JSON_VALUE)
                .header(HttpHeaders.USER_AGENT,USER_AGENT).retrieve().body(JsonNode.class);
    }
    private Set<String> supportedCves(){return patchCves.findAll().stream().map(x->x.getCveId().toUpperCase(Locale.ROOT)).collect(Collectors.toSet());}
    private ThreatIntelSourceSyncResult failed(String source,Exception error){
        log.warn("{} sync failed",source,error);String message=error.getMessage()==null?error.getClass().getSimpleName():error.getMessage();
        return new ThreatIntelSourceSyncResult(source,0,0,0,0,0,"FAILED",limit(message,500));
    }

    private static JsonNode firstMetric(JsonNode metrics){
        for(String key:List.of("cvssMetricV40","cvssMetricV31","cvssMetricV30","cvssMetricV2")){JsonNode values=metrics.path(key);if(values.isArray()&&values.size()>0)return values.get(0);}return null;
    }
    private static String englishValue(JsonNode values){
        if(!values.isArray())return null;for(JsonNode value:values)if("en".equalsIgnoreCase(text(value,"lang")))return text(value,"value");return values.size()>0?text(values.get(0),"value"):null;
    }
    private static String firstWeakness(JsonNode values){
        if(!values.isArray())return null;for(JsonNode weakness:values){String value=englishValue(weakness.path("description"));if(value!=null&&value.startsWith("CWE-"))return value;}return null;
    }
    private static String findFirstCpe(JsonNode node){
        if(node==null||node.isMissingNode())return null;
        if(node.isObject()){JsonNode matches=node.path("cpeMatch");if(matches.isArray())for(JsonNode match:matches){String value=text(match,"criteria");if(value!=null)return value;}Iterator<JsonNode> children=node.elements();while(children.hasNext()){String value=findFirstCpe(children.next());if(value!=null)return value;}}
        else if(node.isArray())for(JsonNode child:node){String value=findFirstCpe(child);if(value!=null)return value;}return null;
    }
    private static String[] parseCpe(String cpe){if(cpe==null)return new String[]{null,null};String[] values=cpe.split(":",7);return values.length>=5?new String[]{cleanCpe(values[3]),cleanCpe(values[4])}:new String[]{null,null};}
    private static String cleanCpe(String value){return value==null||"*".equals(value)?null:value.replace('_',' ');}
    private static Severity severityFrom(Double score,String label){
        if(label!=null){String normalized=label.toUpperCase(Locale.ROOT);if("MODERATE".equals(normalized))normalized="MEDIUM";try{return Severity.valueOf(normalized);}catch(Exception ignored){}}
        if(score==null)return Severity.UNKNOWN;if(score>=9)return Severity.CRITICAL;if(score>=7)return Severity.HIGH;if(score>=4)return Severity.MEDIUM;if(score>0)return Severity.LOW;return Severity.UNKNOWN;
    }
    private static LocalDate parseDate(String value){if(value==null)return null;try{return OffsetDateTime.parse(value).toLocalDate();}catch(Exception ignored){}try{return LocalDate.parse(value.substring(0,Math.min(10,value.length())));}catch(Exception ignored){return null;}}
    private static String mergeSource(String current,String source){LinkedHashSet<String> values=new LinkedHashSet<>();if(current!=null)Arrays.stream(current.split("\\s*·\\s*")).filter(x->!x.isBlank()).forEach(values::add);if(source!=null&&!source.isBlank())values.add(source);return String.join(" · ",values);}
    private static String upperCve(String value){if(value==null)return null;String v=value.trim().toUpperCase(Locale.ROOT);return v.matches("CVE-\\d{4}-\\d{4,}")?v:null;}
    private static String text(JsonNode n,String k){return n!=null&&n.hasNonNull(k)?n.get(k).asText():null;}
    private static String limit(String value,int max){return value!=null&&value.length()>max?value.substring(0,max):value;}
    private static boolean isBlank(String value){return value==null||value.isBlank();}
    private static String or(String value,String fallback){return isBlank(value)?fallback:value;}
    private static String nullSafe(String value){return value==null?"-":value;}
    private static void setIfPresent(java.util.function.Consumer<String> setter,String value){if(!isBlank(value))setter.accept(value);}

    private record FeedResponse(JsonNode body,String source){}
    private record UpsertCount(int created,int updated){}
    private record Incoming(String cveId,String title,String description,String vendor,String product,Double cvss,
                            Severity severity,String cwe,String vector,LocalDate published,String reference,
                            String affectedRange,String fixedVersion,String source){}
}
