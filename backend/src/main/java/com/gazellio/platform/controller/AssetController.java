package com.gazellio.platform.controller;

import com.gazellio.platform.dto.ApiDtos.AssetScopeOptions;
import com.gazellio.platform.dto.ApiDtos.AssetView;
import com.gazellio.platform.service.AssetService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/assets")
@RequiredArgsConstructor
public class AssetController {
    private final AssetService service;
    @GetMapping public List<AssetView> list(){return service.list();}
    @GetMapping("/scan-targets") public List<AssetView> scanTargets(){return service.scanTargets();}
    @GetMapping("/scope-options") public AssetScopeOptions scopeOptions(){return service.scopeOptions();}
    @GetMapping("/{id}") public AssetView get(@PathVariable Long id){return service.get(id);}
}
