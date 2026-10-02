package com.gazellio.platform.controller;

import com.gazellio.platform.dto.ComplianceDtos.*;
import com.gazellio.platform.service.ComplianceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/compliance")
@RequiredArgsConstructor
public class ComplianceController {
    private final ComplianceService compliance;

    @GetMapping("/summary") public ComplianceSummary summary(){ return compliance.summary(); }
    @GetMapping("/alerts") public List<AlertView> alerts(){ return compliance.openAlerts(); }
    @PreAuthorize("hasAnyAuthority('COMPLIANCE_MANAGE','REGULATORY_MANAGE')")
    @PostMapping("/alerts/{id}/acknowledge") public AlertView acknowledge(@PathVariable Long id){ return compliance.acknowledgeAlert(id); }

    @GetMapping("/exceptions") public List<ExceptionView> exceptions(){ return compliance.exceptionRequests(); }
    @PreAuthorize("hasAnyAuthority('VULNERABILITY_EXEMPT','VULNERABILITY_MANAGE')")
    @PostMapping("/exceptions") public ExceptionView requestException(@Valid @RequestBody ExceptionCreateRequest request){ return compliance.requestException(request); }
    @PreAuthorize("hasAnyAuthority('EXCEPTION_APPROVE','COMPLIANCE_MANAGE')")
    @PostMapping("/exceptions/{id}/approve") public ExceptionView approveException(@PathVariable Long id,@Valid @RequestBody DecisionRequest request){ return compliance.decideException(id,true,request); }
    @PreAuthorize("hasAnyAuthority('EXCEPTION_APPROVE','COMPLIANCE_MANAGE')")
    @PostMapping("/exceptions/{id}/reject") public ExceptionView rejectException(@PathVariable Long id,@Valid @RequestBody DecisionRequest request){ return compliance.decideException(id,false,request); }
    @PreAuthorize("hasAnyAuthority('EXCEPTION_APPROVE','COMPLIANCE_MANAGE')")
    @PostMapping("/exceptions/{id}/revoke") public ExceptionView revokeException(@PathVariable Long id,@Valid @RequestBody DecisionRequest request){ return compliance.revokeException(id,request); }

    @GetMapping("/lifecycle") public List<LifecycleView> lifecycle(){ return compliance.lifecycleRecords(); }
    @PreAuthorize("hasAnyAuthority('LIFECYCLE_MANAGE','COMPLIANCE_MANAGE')")
    @PostMapping("/lifecycle") public LifecycleView createLifecycle(@Valid @RequestBody LifecycleSaveRequest request){ return compliance.saveLifecycle(null,request); }
    @PreAuthorize("hasAnyAuthority('LIFECYCLE_MANAGE','COMPLIANCE_MANAGE')")
    @PutMapping("/lifecycle/{id}") public LifecycleView updateLifecycle(@PathVariable Long id,@Valid @RequestBody LifecycleSaveRequest request){ return compliance.saveLifecycle(id,request); }
    @PreAuthorize("hasAnyAuthority('LIFECYCLE_MANAGE','COMPLIANCE_MANAGE')")
    @DeleteMapping("/lifecycle/{id}") public void deleteLifecycle(@PathVariable Long id){ compliance.deleteLifecycle(id); }

    @GetMapping("/regulatory-cases") public List<RegulatoryCaseView> regulatoryCases(){ return compliance.regulatoryCases(); }
    @PreAuthorize("hasAnyAuthority('REGULATORY_MANAGE','COMPLIANCE_MANAGE')")
    @PostMapping("/regulatory-cases") public RegulatoryCaseView createRegulatoryCase(@Valid @RequestBody RegulatoryCreateRequest request){ return compliance.createRegulatoryCase(request); }
    @PreAuthorize("hasAnyAuthority('REGULATORY_MANAGE','COMPLIANCE_MANAGE')")
    @PutMapping("/regulatory-cases/{id}") public RegulatoryCaseView updateRegulatoryCase(@PathVariable Long id,@Valid @RequestBody RegulatoryUpdateRequest request){ return compliance.updateRegulatoryCase(id,request); }

    @GetMapping("/risk-overrides") public List<RiskOverrideView> riskOverrides(){ return compliance.riskOverrides(); }
    @PreAuthorize("hasAnyAuthority('RISK_OVERRIDE_REQUEST','VULNERABILITY_MANAGE')")
    @PostMapping("/risk-overrides") public RiskOverrideView requestRiskOverride(@Valid @RequestBody RiskOverrideCreateRequest request){ return compliance.requestRiskOverride(request); }
    @PreAuthorize("hasAnyAuthority('RISK_OVERRIDE_APPROVE','COMPLIANCE_MANAGE')")
    @PostMapping("/risk-overrides/{id}/approve") public RiskOverrideView approveRiskOverride(@PathVariable Long id,@Valid @RequestBody DecisionRequest request){ return compliance.decideRiskOverride(id,true,request); }
    @PreAuthorize("hasAnyAuthority('RISK_OVERRIDE_APPROVE','COMPLIANCE_MANAGE')")
    @PostMapping("/risk-overrides/{id}/reject") public RiskOverrideView rejectRiskOverride(@PathVariable Long id,@Valid @RequestBody DecisionRequest request){ return compliance.decideRiskOverride(id,false,request); }

    @PreAuthorize("hasAuthority('AUDIT_VIEW')")
    @GetMapping("/audit-integrity") public AuditIntegrityView auditIntegrity(){ return compliance.auditIntegrity(); }
}
