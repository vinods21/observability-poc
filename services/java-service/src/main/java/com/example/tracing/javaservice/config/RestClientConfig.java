package com.example.tracing.javaservice.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class RestClientConfig {

    @Bean
    RestClient kotlinServiceRestClient(RestClient.Builder builder, KotlinServiceProperties properties) {
        return builder.baseUrl(properties.baseUrl()).build();
    }
}
