package com.gazellio.platform.service;
import com.gazellio.platform.dto.ApiDtos.AssetScopeOptions;
import com.gazellio.platform.dto.ApiDtos.AssetView;
import com.gazellio.platform.dto.ApiDtos.PagedView;
import com.gazellio.platform.dto.ApiDtos.CmdbClassOption;
import com.gazellio.platform.config.CmdbProperties;
import com.gazellio.platform.model.Asset;
import com.gazellio.platform.repository.AssetRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AssetService {
    private final AssetRepository assets;
    private final ViewService view;
    private final CmdbProperties cmdbProperties;
    private final AssetEligibilityPolicy eligibility;

    public List<AssetView> list(){return view.assetViews(managedAssets());}
    public PagedView<AssetView> page(String q,String environment,String ciClass,int page,int size){
        int safePage=Math.max(0,page),safeSize=Math.max(1,Math.min(100,size));
        Specification<Asset> spec=(root,query,cb)->cb.isTrue(root.get("active"));
        if(cmdbProperties.externalInventory())spec=spec.and((root,query,cb)->cb.equal(root.get("sourceSystem"),"CMDB"));
        if(environment!=null&&!environment.isBlank()&&!"ALL".equalsIgnoreCase(environment))spec=spec.and((root,query,cb)->cb.equal(root.get("environment"),com.gazellio.platform.model.Enums.EnvironmentType.valueOf(environment.toUpperCase())));
        if(ciClass!=null&&!ciClass.isBlank()&&!"ALL".equalsIgnoreCase(ciClass))spec=spec.and((root,query,cb)->cb.or(cb.equal(root.get("cmdbClassKey"),ciClass),cb.equal(root.get("assetType"),ciClass)));
        if(q!=null&&!q.isBlank()){String pattern="%"+q.trim().toLowerCase()+"%";spec=spec.and((root,query,cb)->cb.or(cb.like(cb.lower(root.get("assetCode")),pattern),cb.like(cb.lower(root.get("name")),pattern),cb.like(cb.lower(root.get("ipAddress")),pattern),cb.like(cb.lower(root.get("networkSegment")),pattern),cb.like(cb.lower(root.get("businessService")),pattern),cb.like(cb.lower(root.get("ownerName")),pattern),cb.like(cb.lower(root.get("installedProducts")),pattern),cb.like(cb.lower(root.get("cmdbClassName")),pattern)));}
        var result=assets.findAll(spec,PageRequest.of(safePage,safeSize,Sort.by(Sort.Order.asc("name"),Sort.Order.asc("id"))));
        return new PagedView<>(view.assetViews(result.getContent()),result.getTotalElements(),result.getNumber()+1,result.getSize(),Math.max(1,result.getTotalPages()));
    }
    public List<AssetView> scanTargets(){return scanTargets(null,50);}
    public List<AssetView> scanTargets(String q,int limit){
        String needle=q==null?"":q.trim().toLowerCase();
        int safeLimit=Math.max(1,Math.min(100,limit));
        return view.assetViews(managedAssets().stream().filter(eligibility::scannable)
                .filter(a->needle.isBlank()||contains(a.getName(),needle)||contains(a.getAssetCode(),needle)
                        ||contains(a.getIpAddress(),needle)||contains(a.getNetworkSegment(),needle)
                        ||contains(a.getHostname(),needle)||contains(a.getBusinessService(),needle)
                        ||contains(a.getCmdbClassName(),needle))
                .limit(safeLimit).toList());
    }
    public AssetView get(Long id){return view.asset(assets.findById(id).orElseThrow());}

    public AssetScopeOptions scopeOptions() {
        List<Asset> rows = managedAssets().stream().filter(eligibility::scannable).toList();
        Map<String,List<Asset>> byClass=rows.stream().filter(a->a.getCmdbClassKey()!=null)
                .collect(Collectors.groupingBy(Asset::getCmdbClassKey,LinkedHashMap::new,Collectors.toList()));
        List<CmdbClassOption> classes=byClass.entrySet().stream().map(entry->{Asset first=entry.getValue().getFirst();return new CmdbClassOption(entry.getKey(),first.getCmdbClassName(),entry.getValue().size());})
                .sorted(Comparator.comparing(CmdbClassOption::name,Comparator.nullsLast(Comparator.naturalOrder()))).toList();
        return new AssetScopeOptions(values(rows, Asset::getNetworkSegment), values(rows, Asset::getAssetType),
                values(rows, Asset::getBusinessService), values(rows, Asset::getOsName),classes);
    }

    private List<Asset> managedAssets(){
        // Production external-inventory mode is fail-closed: if synchronization is unavailable,
        // return an empty inventory instead of presenting local demo rows as real configuration items.
        return cmdbProperties.externalInventory()
                ?assets.findBySourceSystemAndActiveTrueOrderByNameAsc("CMDB")
                :assets.findByActiveTrueOrderByNameAsc();
    }

    private List<String> values(List<Asset> rows, java.util.function.Function<Asset,String> getter) {
        return rows.stream().map(getter).filter(Objects::nonNull).map(String::trim).filter(v -> !v.isBlank())
                .distinct().sorted(Comparator.naturalOrder()).toList();
    }
    private boolean contains(String value,String needle){return value!=null&&value.toLowerCase().contains(needle);}
}
