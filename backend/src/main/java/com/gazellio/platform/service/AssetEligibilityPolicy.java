package com.gazellio.platform.service;

import com.gazellio.platform.model.Asset;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Set;

/** Keeps inventory CIs separate from assets that can actually be scanned or patched. */
@Component
public class AssetEligibilityPolicy {
    private static final Set<String> TECHNICAL_TYPES=Set.of(
            "SERVER","VIRTUAL_MACHINE","PHYSICAL_SERVER","DATABASE","MIDDLEWARE","CONTAINER",
            "NETWORK_DEVICE","FILE_SERVICE","SECURITY_COMPONENT","APPLICATION_RUNTIME");
    private static final Set<String> NON_TARGET_CLASSES=Set.of(
            "business","application","service","logic_subsystem","physical_subsystem",
            "deployment_unit","idcrack","idc");

    public boolean scannable(Asset asset){
        if(asset==null||!asset.isActive()||blank(asset.getIpAddress()))return false;
        String classKey=lower(asset.getCmdbClassKey());
        if(NON_TARGET_CLASSES.contains(classKey))return false;
        return TECHNICAL_TYPES.contains(upper(asset.getAssetType()));
    }

    public boolean executionReachable(Asset asset){
        if(!scannable(asset))return false;
        // Imported CIs may be operated by SSH/WinRM or an enterprise deployment connector;
        // an Agent heartbeat is therefore not the only valid execution channel.
        return "CMDB".equalsIgnoreCase(asset.getSourceSystem())||"ONLINE".equalsIgnoreCase(asset.getAgentStatus());
    }

    private boolean blank(String value){return value==null||value.isBlank();}
    private String lower(String value){return value==null?"":value.trim().toLowerCase(Locale.ROOT);}
    private String upper(String value){return value==null?"":value.trim().toUpperCase(Locale.ROOT);}
}
