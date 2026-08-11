package com.darmoz.auth.config;

import com.darmoz.auth.util.PemUtils;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Path;
import java.security.PrivateKey;
import java.security.PublicKey;

@Configuration
@EnableConfigurationProperties(JwtProperties.class)
public class RsaKeyConfig {

    @Bean
    public PrivateKey jwtSigningKey(JwtProperties properties) {
        return PemUtils.readPrivateKey(Path.of(properties.getPrivateKeyPath()));
    }

    @Bean
    public PublicKey jwtVerificationKey(JwtProperties properties) {
        return PemUtils.readPublicKey(Path.of(properties.getPublicKeyPath()));
    }
}
