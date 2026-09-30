package com.gazellio.platform.service;

import com.gazellio.platform.dto.ApiDtos.GlobalSearchResult;
import com.gazellio.platform.model.*;
import com.gazellio.platform.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class GlobalSearchService {
    private final VulnerabilityDefinitionRepository vulnerabilities;
    private final AssetRepository assets;
    private final RemediationTaskRepository tasks;
    private final PatchRepository patches;

    public List<GlobalSearchResult> search(String raw) {
        String needle=raw==null?"":raw.trim().toLowerCase(Locale.ROOT);
        if(needle.length()<2)return List.of();
        String pattern="%"+needle+"%";
        List<GlobalSearchResult> result=new ArrayList<>();
        for(VulnerabilityDefinition v:vulnerabilities.search(pattern,PageRequest.of(0,5))){
            result.add(new GlobalSearchResult("VULNERABILITY",v.getCveId(),v.getTitleZh(),v.getTitleEn(),
                    v.getVendor()+" · "+v.getProduct(),v.getVendor()+" · "+v.getProduct(),
                    "/vulnerabilities/library/"+v.getCveId()));
        }
        for(Asset a:assets.search(pattern,PageRequest.of(0,5))){
            result.add(new GlobalSearchResult("ASSET",String.valueOf(a.getId()),a.getName(),a.getName(),
                    a.getAssetCode()+" · "+a.getIpAddress(),a.getAssetCode()+" · "+a.getIpAddress(),
                    "/assets/"+a.getId()));
        }
        for(RemediationTask t:tasks.search(pattern,PageRequest.of(0,5))){
            result.add(new GlobalSearchResult("TASK",String.valueOf(t.getId()),t.getTaskNo(),t.getTaskNo(),
                    "处置任务 · "+t.getPriority(),"Remediation task · "+t.getPriority(),"/tasks/"+t.getId()));
        }
        for(Patch p:patches.search(pattern,PageRequest.of(0,5))){
            result.add(new GlobalSearchResult("PATCH",String.valueOf(p.getId()),p.getTitleZh(),p.getTitleEn(),
                    p.getPatchId()+" · "+p.getProduct(),p.getPatchId()+" · "+p.getProduct(),"/patches/"+p.getId()));
        }
        return result.stream().limit(12).toList();
    }
}
