package com.gazellio.platform.controller;

import com.gazellio.platform.dto.ApiDtos.CmdbAssetDetailView;
import com.gazellio.platform.dto.ApiDtos.CmdbSyncView;
import com.gazellio.platform.service.CmdbSyncService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/cmdb") @RequiredArgsConstructor
public class CmdbController {
    private final CmdbSyncService service;
    @GetMapping("/status") @PreAuthorize("hasAuthority('ASSET_VIEW')") public CmdbSyncView status(){return service.status();}
    @PostMapping("/sync") @PreAuthorize("hasAuthority('ASSET_SYNC')") public CmdbSyncView sync(){return service.synchronize();}
    @GetMapping("/assets/{localAssetId}") @PreAuthorize("hasAuthority('ASSET_VIEW')") public CmdbAssetDetailView detail(@PathVariable Long localAssetId){return service.detail(localAssetId);}
}
