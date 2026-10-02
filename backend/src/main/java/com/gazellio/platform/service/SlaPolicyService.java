package com.gazellio.platform.service;

import com.gazellio.platform.dto.ApiDtos.SlaDecisionView;
import com.gazellio.platform.model.Asset;
import com.gazellio.platform.model.Enums.Severity;
import com.gazellio.platform.model.VulnerabilityDefinition;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/** Auditable three-dimensional SLA policy: threat intelligence x asset criticality x exposure. */
@Service
@RequiredArgsConstructor
public class SlaPolicyService {
    private final SettingsService settings;

    public SlaDecisionView decide(VulnerabilityDefinition vulnerability, Asset asset) {
        int threat = vulnerability.isKev() ? 3
                : vulnerability.getSeverity() == Severity.CRITICAL || vulnerability.getSeverity() == Severity.HIGH ? 2 : 1;
        int criticality = asset.getCriticality() == null ? 3 : asset.getCriticality();
        int assetLevel = criticality >= 5 ? 3 : criticality >= 3 ? 2 : 1;
        int exposure = Boolean.TRUE.equals(asset.getInternetExposed()) ? 2 : 1;
        List<String> reasons = new ArrayList<>();
        if (vulnerability.isKev()) reasons.add("KNOWN_EXPLOITED");
        if (vulnerability.getSeverity() == Severity.CRITICAL) reasons.add("CRITICAL_SEVERITY");
        if (assetLevel == 3) reasons.add("CRITICAL_ASSET");
        if (exposure == 2) reasons.add("INTERNET_EXPOSED");
        if (asset.getEnvironment() != null && "PROD".equals(asset.getEnvironment().name())) reasons.add("PRODUCTION");

        String priority;
        if (threat == 3 || threat == 2 && (assetLevel == 3 || exposure == 2)) priority = "P1";
        else if (threat == 2 || assetLevel == 3 || exposure == 2) priority = "P2";
        else if (threat + assetLevel + exposure >= 4) priority = "P3";
        else priority = "P4";
        int fallback = switch (priority) { case "P1" -> 3; case "P2" -> 7; case "P3" -> 30; default -> 90; };
        int days = settings.intValue("sla" + priority + "Days", fallback, 1, 3650);
        return new SlaDecisionView("T" + threat, "A" + assetLevel, "E" + exposure, priority, days, reasons);
    }
}
