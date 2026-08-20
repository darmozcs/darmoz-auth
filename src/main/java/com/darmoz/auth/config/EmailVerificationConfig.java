package com.darmoz.auth.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties({EmailVerificationProperties.class, DarmozMailProperties.class})
public class EmailVerificationConfig {

    @Bean
    public RestClient darmozMailRestClient(DarmozMailProperties properties) {
        return RestClient.builder()
                .baseUrl(properties.getBaseUrl())
                .build();
    }
}
