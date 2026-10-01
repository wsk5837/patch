package com.gazellio.platform.controller;

import com.gazellio.platform.dto.ApiDtos.AuditView;
import com.gazellio.platform.repository.AuditEventRepository;
import com.gazellio.platform.service.ViewService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.time.*;
import java.util.*;

@RestController @RequestMapping("/api/audit") @RequiredArgsConstructor
public class AuditController {
    private final AuditEventRepository repo;
    private final ViewService view;

    @GetMapping
    public List<AuditView> recent(@RequestParam(required=false) String actor,
                                  @RequestParam(required=false) String entityType,
                                  @RequestParam(required=false) String action,
                                  @RequestParam(required=false) String from,
                                  @RequestParam(required=false) String to) {
        Instant start=parseStart(from),end=parseEnd(to);
        return repo.findTop500ByOrderByCreatedAtDesc().stream()
                .filter(x->blank(actor)||contains(x.getActor(),actor))
                .filter(x->blank(entityType)||x.getEntityType().equalsIgnoreCase(entityType))
                .filter(x->blank(action)||contains(x.getAction(),action))
                .filter(x->start==null||!x.getCreatedAt().isBefore(start))
                .filter(x->end==null||x.getCreatedAt().isBefore(end))
                .map(view::audit).toList();
    }

    private boolean blank(String value){return value==null||value.isBlank()||"ALL".equalsIgnoreCase(value);}
    private boolean contains(String value,String needle){return value!=null&&value.toLowerCase(Locale.ROOT).contains(needle.toLowerCase(Locale.ROOT));}
    private Instant parseStart(String value){try{return blank(value)?null:LocalDate.parse(value).atStartOfDay(ZoneOffset.UTC).toInstant();}catch(Exception ignored){return null;}}
    private Instant parseEnd(String value){try{return blank(value)?null:LocalDate.parse(value).plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();}catch(Exception ignored){return null;}}
}
