package com.gazellio.platform.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gazellio.platform.dto.ApiDtos.ReportSnapshotView;
import com.gazellio.platform.model.ReportSnapshot;
import com.gazellio.platform.repository.ReportSnapshotRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service @RequiredArgsConstructor
public class ReportSnapshotService {
    private final ReportSnapshotRepository snapshots;
    private final DashboardReportService reports;
    private final CurrentUserService currentUser;
    private final AuditService audit;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly=true)
    public List<ReportSnapshotView> recent(){return snapshots.findTop50ByOrderByCreatedAtDesc().stream().map(this::view).toList();}

    @Transactional
    public ReportSnapshotView create(int requestedDays){
        int days=Math.max(7,Math.min(365,requestedDays));
        Instant now=Instant.now();
        String no="RPT-"+DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneOffset.UTC).format(now)+"-"+String.format("%03d",Math.floorMod(now.toEpochMilli(),1000));
        String payload;
        try{payload=objectMapper.writeValueAsString(reports.report(days));}
        catch(JsonProcessingException ex){throw new IllegalStateException("Unable to serialize report snapshot",ex);}
        ReportSnapshot saved=snapshots.save(ReportSnapshot.builder().snapshotNo(no).windowDays(days)
                .createdBy(currentUser.name()).createdAt(now).reportJson(payload).build());
        audit.log("REPORT_SNAPSHOT",saved.getId(),"CREATE","固化监管报表快照 "+no,"Created regulatory report snapshot "+no,currentUser.name());
        return view(saved);
    }

    @Transactional(readOnly=true)
    public String payload(Long id){return snapshots.findById(id).orElseThrow().getReportJson();}

    private ReportSnapshotView view(ReportSnapshot row){return new ReportSnapshotView(row.getId(),row.getSnapshotNo(),row.getWindowDays(),row.getCreatedBy(),row.getCreatedAt().toString());}
}
