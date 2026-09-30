package com.gazellio.platform.controller;

import com.gazellio.platform.dto.ApiDtos.GlobalSearchResult;
import com.gazellio.platform.service.GlobalSearchService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/search")
@RequiredArgsConstructor
public class GlobalSearchController {
    private final GlobalSearchService service;
    @GetMapping public List<GlobalSearchResult> search(@RequestParam String q){return service.search(q);}
}
