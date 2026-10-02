package com.gazellio.platform.service;

import com.gazellio.platform.model.Asset;
import com.gazellio.platform.model.VulnerabilityDefinition;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Locale;

@Service
@RequiredArgsConstructor
public class RiskAssessmentService {
    private final SettingsService settings;

    public Assessment assess(VulnerabilityDefinition vulnerability, Asset asset){
        double cvss=vulnerability.getCvss()==null?settings.doubleValue("riskUnscoredBase",5.0,0,10):vulnerability.getCvss();
        double criticality=Math.max(0,(asset.getCriticality()==null?3:asset.getCriticality())-3)
                *settings.doubleValue("riskCriticalityWeight",0.3,0,3);
        double kev=vulnerability.isKev()?settings.doubleValue("riskKevWeight",0.6,0,5):0;
        double exposure=Boolean.TRUE.equals(asset.getInternetExposed())?settings.doubleValue("riskExposureWeight",0.8,0,5):0;
        double production=asset.getEnvironment()!=null&&"PROD".equals(asset.getEnvironment().name())
                ?settings.doubleValue("riskProductionWeight",0.2,0,5):0;
        double score=round(Math.min(10,Math.max(0,cvss+criticality+kev+exposure+production)));
        String version=settings.value("riskFormulaVersion","RISK-2026.1");
        String factors=String.format(Locale.ROOT,
                "{\"cvss\":%.1f,\"criticality\":%.1f,\"knownExploited\":%.1f,\"internetExposure\":%.1f,\"production\":%.1f,\"formulaVersion\":\"%s\"}",
                cvss,criticality,kev,exposure,production,escape(version));
        return new Assessment(score,version,factors);
    }

    private double round(double value){return Math.round(value*10.0)/10.0;}
    private String escape(String value){return value.replace("\\","\\\\").replace("\"","\\\"");}
    public record Assessment(double score,String version,String factors){}
}
