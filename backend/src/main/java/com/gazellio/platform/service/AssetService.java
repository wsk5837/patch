package com.gazellio.platform.service;
import com.gazellio.platform.dto.ApiDtos.AssetScopeOptions;
import com.gazellio.platform.dto.ApiDtos.AssetView;
import com.gazellio.platform.dto.ApiDtos.CmdbClassOption;
import com.gazellio.platform.config.CmdbProperties;
import com.gazellio.platform.model.Asset;
import com.gazellio.platform.repository.AssetRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
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

    public List<AssetView> list(){return view.assetViews(managedAssets());}
    public AssetView get(Long id){return view.asset(assets.findById(id).orElseThrow());}

    public AssetScopeOptions scopeOptions() {
        List<Asset> rows = managedAssets();
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
}
