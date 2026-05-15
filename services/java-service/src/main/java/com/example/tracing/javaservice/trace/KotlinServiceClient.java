package com.example.tracing.javaservice.trace;

import com.example.tracing.javaservice.config.KotlinServiceProperties;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class KotlinServiceClient {

    private final RestClient restClient;
    private final KotlinServiceProperties properties;

    public KotlinServiceClient(RestClient kotlinServiceRestClient, KotlinServiceProperties properties) {
        this.restClient = kotlinServiceRestClient;
        this.properties = properties;
    }

    public KotlinTraceResponse invokeTraceFlow(KotlinTraceRequest request) {
        return restClient.post()
                .uri(properties.tracePath())
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(KotlinTraceResponse.class);
    }
}
