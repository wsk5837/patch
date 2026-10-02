package com.gazellio.platform;

import com.gazellio.platform.model.Asset;
import com.gazellio.platform.model.Enums.EnvironmentType;
import com.gazellio.platform.model.Enums.Severity;
import com.gazellio.platform.model.VulnerabilityDefinition;
import com.gazellio.platform.service.SettingsService;
import com.gazellio.platform.service.SlaPolicyService;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SlaPolicyServiceTest {
    private final SettingsService settings = mock(SettingsService.class);
    private final SlaPolicyService policy = new SlaPolicyService(settings);

    @Test void knownExploitedCriticalInternetAssetIsP1() {
        when(settings.intValue(eq("slaP1Days"), anyInt(), anyInt(), anyInt())).thenReturn(2);
        var vulnerability = VulnerabilityDefinition.builder().cveId("CVE-TEST-1").severity(Severity.HIGH).kev(true).build();
        var asset = Asset.builder().assetCode("CI-1").name("server-1").environment(EnvironmentType.PROD)
                .criticality(5).internetExposed(true).build();
        var decision = policy.decide(vulnerability, asset);
        assertThat(decision.priority()).isEqualTo("P1");
        assertThat(decision.slaDays()).isEqualTo(2);
        assertThat(decision.reasons()).contains("KNOWN_EXPLOITED", "CRITICAL_ASSET", "INTERNET_EXPOSED", "PRODUCTION");
    }

    @Test void internalStandardAssetUsesLowerPriority() {
        when(settings.intValue(eq("slaP3Days"), anyInt(), anyInt(), anyInt())).thenReturn(30);
        var vulnerability = VulnerabilityDefinition.builder().cveId("CVE-TEST-2").severity(Severity.MEDIUM).build();
        var asset = Asset.builder().assetCode("CI-2").name("server-2").environment(EnvironmentType.TEST)
                .criticality(3).internetExposed(false).build();
        var decision = policy.decide(vulnerability, asset);
        assertThat(decision.priority()).isEqualTo("P3");
        assertThat(decision.threatLevel()).isEqualTo("T1");
        assertThat(decision.exposureLevel()).isEqualTo("E1");
    }
}
