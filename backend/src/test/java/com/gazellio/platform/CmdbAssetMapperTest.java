package com.gazellio.platform;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gazellio.platform.model.Asset;
import com.gazellio.platform.service.CmdbAssetMapper;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class CmdbAssetMapperTest {
    private final ObjectMapper json = new ObjectMapper();
    private final CmdbAssetMapper mapper = new CmdbAssetMapper();

    @Test
    void mapsAuthoritativeMongoConfigurationItemWithoutInventingIdentity() throws Exception {
        JsonNode row = json.readTree("""
                {
                  "id": "09fd5886-18f7-4a1c-abfa-6aaa12704877",
                  "key": "mongodb",
                  "enabled": true,
                  "locked": false,
                  "updated_at": "2026-05-06 15:52:11",
                  "name": "mongo-prod-y01",
                  "ci_number": "CI202601000007",
                  "database_ip_address": "192.174.167.139",
                  "database_port": "12701",
                  "database_app_version": "5",
                  "ci#classname": "Mongodb",
                  "env_#value": "云环境-互联网",
                  "state_#value": "运行中"
                }
                """);
        Instant synchronizedAt = Instant.parse("2026-10-01T11:00:00Z");

        Asset asset = mapper.apply(new Asset(), row, "mongodb", "Mongodb", synchronizedAt);

        assertThat(mapper.proposedCode(row, "mongodb")).isEqualTo("CI202601000007");
        assertThat(asset.getCmdbItemId()).isEqualTo("09fd5886-18f7-4a1c-abfa-6aaa12704877");
        assertThat(asset.getName()).isEqualTo("mongo-prod-y01");
        assertThat(asset.getIpAddress()).isEqualTo("192.174.167.139");
        assertThat(asset.getNetworkSegment()).isEqualTo("192.174.167.0/24");
        assertThat(asset.getCmdbClassKey()).isEqualTo("mongodb");
        assertThat(asset.getCmdbClassName()).isEqualTo("Mongodb");
        assertThat(asset.getAssetType()).isEqualTo("DATABASE");
        assertThat(asset.getOsVersion()).isEqualTo("5");
        assertThat(asset.getInstalledProducts()).isEqualTo("MongoDB 5");
        assertThat(asset.getCmdbState()).isEqualTo("运行中");
        assertThat(asset.getSourceSystem()).isEqualTo("CMDB");
        assertThat(asset.getCmdbSyncedAt()).isEqualTo(synchronizedAt);
        assertThat(asset.isActive()).isTrue();
    }
}
