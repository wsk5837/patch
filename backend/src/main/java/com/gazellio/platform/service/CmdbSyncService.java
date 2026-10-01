package com.gazellio.platform.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.gazellio.platform.config.CmdbProperties;
import com.gazellio.platform.dto.ApiDtos.*;
import com.gazellio.platform.model.Asset;
import com.gazellio.platform.repository.AssetRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.locks.ReentrantLock;

@Service
@RequiredArgsConstructor
@Slf4j
public class CmdbSyncService {
    private final CmdbProperties properties;
    private final CmdbClient client;
    private final CmdbAssetMapper mapper;
    private final AssetRepository assets;
    private final AuditService audit;
    private final CurrentUserService currentUser;
    private final ReentrantLock syncLock=new ReentrantLock();
    private volatile CmdbSyncView last;

    public CmdbSyncView status(){
        CmdbSyncView snapshot=last;
        if(snapshot!=null)return new CmdbSyncView(properties.configured(),syncLock.isLocked(),snapshot.lastStatus(),
                snapshot.lastStartedAt(),snapshot.lastCompletedAt(),snapshot.imported(),snapshot.updated(),
                snapshot.deactivated(),snapshot.remoteTotal(),snapshot.message(),snapshot.classes());
        List<Asset> mirrored=assets.findBySourceSystem("CMDB");
        Optional<Instant> latest=mirrored.stream().map(Asset::getCmdbSyncedAt).filter(Objects::nonNull).max(Comparator.naturalOrder());
        long active=mirrored.stream().filter(Asset::isActive).count();
        String state=active>0?"SUCCESS":"NOT_RUN";
        String message=!properties.configured()?"Asset source connection is not configured":active>0?"Asset inventory is available":"Asset inventory has not been synchronized";
        return new CmdbSyncView(properties.configured(),false,state,null,latest.map(Instant::toString).orElse(null),0,0,0,active,message,List.of());
    }

    public CmdbSyncView synchronize(){
        if(!properties.configured())throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"CMDB integration is not configured");
        if(!syncLock.tryLock())return status();
        Instant started=Instant.now();
        last=new CmdbSyncView(true,true,"RUNNING",started.toString(),null,0,0,0,0,"Synchronizing read-only CMDB assets",List.of());
        int imported=0,updated=0,deactivated=0;long remoteTotal=0;
        List<CmdbClassSyncView> classResults=new ArrayList<>();
        Set<String> seen=new HashSet<>(),successfulClasses=new HashSet<>();
        try{
            int pageSize=Math.max(1,Math.min(500,properties.getPageSize()));
            for(String rawKey:properties.getClassKeys()){
                String key=rawKey==null?"":rawKey.trim();if(key.isBlank())continue;
                int classImported=0,classUpdated=0;long classTotal=0;String className=mapper.className(key);
                try{
                    int page=1,totalPages=1;
                    do{
                        JsonNode response=client.list(key,page,pageSize);
                        JsonNode meta=response.path("meta");className=text(meta,"title",className);
                        classTotal=meta.path("total").asLong(classTotal);totalPages=Math.max(1,meta.path("total_pages").asInt(1));
                        JsonNode rows=response.path("data");
                        if(rows.isArray())for(JsonNode row:rows){
                            String itemId=text(row,"id",null);if(itemId==null)continue;
                            Optional<Asset> existing=assets.findByCmdbItemId(itemId);Asset asset=existing.orElseGet(Asset::new);
                            mapper.apply(asset,row,key,className,started);
                            if(existing.isEmpty())asset.setAssetCode(uniqueCode(mapper.proposedCode(row,key),itemId));
                            assets.save(asset);seen.add(itemId);if(existing.isEmpty()){classImported++;imported++;}else{classUpdated++;updated++;}
                        }
                        page++;
                    }while(page<=totalPages);
                    remoteTotal+=classTotal;successfulClasses.add(key);
                    classResults.add(new CmdbClassSyncView(key,className,classTotal,classImported,classUpdated,"SUCCESS",null));
                }catch(Exception ex){
                    String error=safeMessage(ex);classResults.add(new CmdbClassSyncView(key,className,classTotal,classImported,classUpdated,"FAILED",error));
                    log.warn("Read-only CMDB sync failed for class {}: {}",key,error);
                }
                last=new CmdbSyncView(true,true,"RUNNING",started.toString(),null,imported,updated,deactivated,remoteTotal,
                        "Synchronizing read-only CMDB assets",List.copyOf(classResults));
            }
            if(!successfulClasses.isEmpty()){
                List<Asset> changed=new ArrayList<>();
                for(Asset asset:assets.findBySourceSystem("CMDB"))if(successfulClasses.contains(asset.getCmdbClassKey())&&!seen.contains(asset.getCmdbItemId())&&asset.isActive()){asset.setActive(false);changed.add(asset);deactivated++;}
                if(properties.isReplaceLocalAssets())for(Asset asset:assets.findBySourceSystem("LOCAL"))if(asset.isActive()){asset.setActive(false);changed.add(asset);deactivated++;}
                if(!changed.isEmpty())assets.saveAll(changed);
            }
            boolean failed=classResults.stream().anyMatch(x->"FAILED".equals(x.status()));
            String state=successfulClasses.isEmpty()?"FAILED":failed?"PARTIAL":"SUCCESS";
            String message=successfulClasses.isEmpty()?"No CMDB classes could be synchronized":
                    "Imported "+imported+", updated "+updated+", deactivated "+deactivated+" local mirror records";
            last=new CmdbSyncView(true,false,state,started.toString(),Instant.now().toString(),imported,updated,deactivated,remoteTotal,message,List.copyOf(classResults));
            audit.log("CMDB","READ_ONLY_SYNC","SYNC","只读同步 CMDB 资产：新增 "+imported+"，更新 "+updated,
                    "Read-only CMDB asset sync: "+imported+" imported, "+updated+" updated",currentUser.name());
            return last;
        }finally{syncLock.unlock();}
    }

    @Scheduled(cron="${app.cmdb.sync-cron:0 0/30 * * * *}")
    public void scheduledSync(){if(properties.configured())try{synchronize();}catch(Exception ex){log.warn("Scheduled CMDB sync failed: {}",safeMessage(ex));}}

    public CmdbAssetDetailView detail(Long localAssetId){
        Asset asset=assets.findById(localAssetId).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Asset not found"));
        if(!"CMDB".equals(asset.getSourceSystem())||asset.getCmdbItemId()==null)throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Asset is not linked to CMDB");
        JsonNode response=client.detail(asset.getCmdbItemId(),1),data=response.path("data"),definition=response.path("ci_class"),topology=response.path("topology_data");
        List<CmdbPropertyView> values=new ArrayList<>();Set<String> added=new HashSet<>();
        JsonNode definitions=definition.path("properties");
        if(definitions.isArray())for(JsonNode property:definitions){String key=text(property,"key",null),label=text(property,"name",key);if(key==null)continue;String value=display(data.get(key));if(value==null)value=display(data.get(key+"_#value"));if(value!=null){values.add(new CmdbPropertyView(key,label,value));added.add(key);}}
        for(String key:List.of("ci_number","ci#classname","state_#value","created_at","updated_at"))if(!added.contains(key)){String value=display(data.get(key));if(value!=null)values.add(new CmdbPropertyView(key,key,value));}
        List<CmdbTopologyNodeView> nodes=new ArrayList<>();JsonNode rawNodes=topology.path("nodes");
        if(rawNodes.isArray())for(JsonNode node:rawNodes){
            String id=text(node,"id",null),label=text(node,"label",id);JsonNode nodeData=node.path("data");
            String itemId=text(nodeData,"uid",null),classKey=text(nodeData,"ci_key",text(nodeData,"key",null));
            String className=text(node,"combo",text(nodeData,"ci#classname",classKey));
            Long linked=itemId==null?null:assets.findByCmdbItemId(itemId).map(Asset::getId).orElse(null);
            nodes.add(new CmdbTopologyNodeView(id,label,classKey,className,linked));
        }
        List<CmdbTopologyEdgeView> edges=new ArrayList<>();JsonNode rawEdges=topology.path("edges");
        if(rawEdges.isArray())for(JsonNode edge:rawEdges)edges.add(new CmdbTopologyEdgeView(text(edge,"id",null),text(edge,"source",null),text(edge,"target",null),text(edge,"label",null),text(edge,"category",null)));
        return new CmdbAssetDetailView(asset.getCmdbItemId(),asset.getCmdbClassKey(),asset.getCmdbClassName(),Instant.now().toString(),values,nodes,edges);
    }

    private String uniqueCode(String proposed,String itemId){String code=proposed;int suffix=1;while(true){Optional<Asset> collision=assets.findByAssetCode(code);if(collision.isEmpty()||Objects.equals(collision.get().getCmdbItemId(),itemId))return code;String tail="-"+(suffix++);code=proposed.substring(0,Math.min(proposed.length(),120-tail.length()))+tail;}}
    private String display(JsonNode value){if(value==null||value.isNull())return null;if(value.isTextual()||value.isNumber()||value.isBoolean()){String text=value.asText().trim();return text.isBlank()?null:text;}return value.isContainerNode()&&value.size()>0?value.toString():null;}
    private String text(JsonNode node,String key,String fallback){if(node==null)return fallback;JsonNode value=node.get(key);if(value==null||value.isNull())return fallback;String text=value.asText().trim();return text.isBlank()?fallback:text;}
    private String safeMessage(Exception ex){String message=ex.getMessage();if(message==null||message.isBlank())message=ex.getClass().getSimpleName();return message.replaceAll("(?i)(OAUTH-CLIENT-(?:ID|SECRET)[=: ]+)[^,; ]+","$1***");}
}
