package com.gazellio.platform.controller;

import com.gazellio.platform.dto.ApiDtos.AssetScopeOptions;
import com.gazellio.platform.dto.ApiDtos.AssetView;
import com.gazellio.platform.dto.ApiDtos.PagedView;
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
    @GetMapping("/page") public PagedView<AssetView> page(@RequestParam(required=false)String q,@RequestParam(required=false)String environment,@RequestParam(required=false)String ciClass,@RequestParam(defaultValue="0")int page,@RequestParam(defaultValue="30")int size){return service.page(q,environment,ciClass,page,size);}
    @GetMapping("/scan-targets") public List<AssetView> scanTargets(){return service.scanTargets();}
    @GetMapping("/scope-options") public AssetScopeOptions scopeOptions(){return service.scopeOptions();}
    @GetMapping("/{id}") public AssetView get(@PathVariable Long id){return service.get(id);}
}
