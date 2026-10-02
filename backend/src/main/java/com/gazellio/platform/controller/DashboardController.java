package com.gazellio.platform.controller;
import com.gazellio.platform.dto.ApiDtos.*;
import com.gazellio.platform.service.DashboardReportService;
import com.gazellio.platform.service.ReportSnapshotService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController @RequestMapping("/api/dashboard") @RequiredArgsConstructor
public class DashboardController {
    private final DashboardReportService service;
    private final ReportSnapshotService snapshots;
    @GetMapping public DashboardView dashboard(){return service.dashboard();}
    @GetMapping("/report") public ReportView report(@RequestParam(defaultValue="30") int days){return service.report(days);}
    @GetMapping("/report/snapshots") public List<ReportSnapshotView> snapshots(){return snapshots.recent();}
    @PostMapping("/report/snapshots") @PreAuthorize("hasAuthority('REPORT_EXPORT')")
    public ReportSnapshotView snapshot(@RequestParam(defaultValue="30") int days){return snapshots.create(days);}
    @GetMapping(value="/report/snapshots/{id}",produces=MediaType.APPLICATION_JSON_VALUE)
    public String snapshotPayload(@PathVariable Long id){return snapshots.payload(id);}
}
