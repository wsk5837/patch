package com.gazellio.platform.service;

import com.gazellio.platform.dto.ApiDtos.*;
import com.gazellio.platform.model.*;
import com.gazellio.platform.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

@Service
@RequiredArgsConstructor
public class PatchService {
    private final PatchRepository patches;
    private final PatchCveRepository patchCves;
    private final VulnerabilityDefinitionRepository vulnerabilities;
    private final PatchServerRepository servers;
    private final PatchDeploymentRepository deployments;
    private final ViewService view;
    private final AuditService audit;
    private final CurrentUserService currentUser;

    public List<PatchView> list(){
        return patches.findAllByOrderByPublishedDateDesc().stream().map(view::patch).toList();
    }

    public PatchView get(Long id){
        return view.patch(patches.findById(id).orElseThrow());
    }

    public List<PatchServerView> servers(){
        return servers.findAllByOrderByNameAsc().stream().map(view::patchServer).toList();
    }

    public List<DeploymentView> deployments(){
        return deployments.findTop200ByOrderByCreatedAtDesc().stream().map(view::deployment).toList();
    }

    @Transactional
    public PatchView register(PatchCreateRequest req){
        Patch p=patches.findByPatchId(req.patchId().trim()).orElseGet(Patch::new);
        p.setPatchId(req.patchId().trim());
        p.setVendor(req.vendor().trim());
        p.setProduct(req.product().trim());
        p.setVersion(blankToNull(req.version()));
        p.setTitleZh(req.titleZh().trim());
        p.setTitleEn(req.titleEn().trim());
        p.setDownloadUrl(blankToNull(req.downloadUrl()));
        p.setChecksum(blankToNull(req.checksum()));
        p.setSizeMb(req.sizeMb());
        p.setRebootRequired(req.rebootRequired());
        p.setStatus("AVAILABLE");
        p.setSource(blankToNull(req.source())==null?"Manual":req.source().trim());
        p.setUpdatedAt(Instant.now());
        p=patches.save(p);

        Set<String> requested=new LinkedHashSet<>();
        if(req.cves()!=null){
            for(String raw:req.cves()){
                if(raw==null||raw.isBlank())continue;
                String cve=raw.trim().toUpperCase(Locale.ROOT);
                requested.add(cve);
                if(!patchCves.existsByPatchIdAndCveId(p.getId(),cve)){
                    patchCves.save(PatchCve.builder().patchId(p.getId()).cveId(cve).build());
                }
                vulnerabilities.findById(cve).ifPresent(v->{v.setPatchAvailable(true);v.setUpdatedAt(Instant.now());vulnerabilities.save(v);});
            }
        }
        for(PatchCve existing:patchCves.findByPatchId(p.getId())){
            if(!requested.contains(existing.getCveId())) patchCves.delete(existing);
        }

        audit.log("PATCH",p.getId(),"REGISTER","登记补丁并更新漏洞映射："+p.getPatchId(),"Patch registered and vulnerability mappings updated: "+p.getPatchId(),currentUser.name());
        return view.patch(p);
    }

    @Transactional
    public int sync(){
        int n=0;
        for(PatchServer s:servers.findAll()){
            s.setLastSyncAt(Instant.now());
            servers.save(s);
            n++;
        }
        audit.log("PATCH_CATALOG","all","SYNC","补丁源同步完成","Patch sources synchronized",currentUser.name());
        return n;
    }

    private static String blankToNull(String value){
        return value==null||value.isBlank()?null:value.trim();
    }
}
