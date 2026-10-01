package com.gazellio.platform.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.gazellio.platform.config.CmdbProperties;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

@Service
public class CmdbClient {
    private final RestClient client;
    private final CmdbProperties properties;

    public CmdbClient(RestClient.Builder builder,CmdbProperties properties){
        this.properties=properties;
        this.client=builder.baseUrl(properties.normalizedBaseUrl())
                .defaultHeader("OAUTH-CLIENT-ID",safe(properties.getClientId()))
                .defaultHeader("OAUTH-CLIENT-SECRET",safe(properties.getClientSecret()))
                .defaultHeader(HttpHeaders.ACCEPT,"application/json").build();
    }

    public JsonNode list(String classKey,int page,int perPage){
        requireConfigured();
        JsonNode body=client.get().uri(uri->uri.path("/api/v1/common/ci_items/{classKey}")
                        .queryParam("page",page).queryParam("per_page",perPage).build(classKey))
                .retrieve().body(JsonNode.class);
        return requireSuccess(body,"CMDB list request failed for "+classKey);
    }

    public JsonNode detail(String itemId,int deep){
        requireConfigured();
        JsonNode body=client.get().uri(uri->uri.path("/api/v1/common/ci_item/{itemId}")
                        .queryParam("deep",Math.max(0,Math.min(3,deep))).build(itemId))
                .retrieve().body(JsonNode.class);
        return requireSuccess(body,"CMDB detail request failed");
    }

    private JsonNode requireSuccess(JsonNode body,String message){
        if(body==null||body.isNull())throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,message+": empty response");
        JsonNode status=body.get("status");
        if(status==null)status=body.get("stauts");
        if(status!=null&&!status.asBoolean())throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,message);
        return body;
    }
    private void requireConfigured(){if(!properties.configured())throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"CMDB integration is not configured");}
    private static String safe(String value){return value==null?"":value;}
}
