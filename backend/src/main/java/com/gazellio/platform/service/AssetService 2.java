package com.gazellio.platform.service;
import com.gazellio.platform.dto.ApiDtos.AssetScopeOptions;
import com.gazellio.platform.dto.ApiDtos.AssetView;
import com.gazellio.platform.model.Asset;
import com.gazellio.platform.repository.AssetRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class AssetService {
    private final AssetRepository assets;
    private final ViewService view;

    public List<AssetView> list(){return view.assetViews(assets.findByActiveTrueOrderByNameAsc());}
    public AssetView get(Long id){return view.asset(assets.findById(id).orElseThrow());}

    public AssetScopeOptions scopeOptions() {
        List<Asset> rows = assets.findByActiveTrueOrderByNameAsc();
        return new AssetScopeOptions(values(rows, Asset::getNetworkSegment), values(rows, Asset::getAssetType),
                values(rows, Asset::getBusinessService), values(rows, Asset::getOsName));
    }

    private List<String> values(List<Asset> rows, java.util.function.Function<Asset,String> getter) {
        return rows.stream().map(getter).filter(Objects::nonNull).map(String::trim).filter(v -> !v.isBlank())
                .distinct().sorted(Comparator.naturalOrder()).toList();
    }
}
