package com.darmoz.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "email-verification")
public class EmailVerificationProperties {

    /** Vigencia del codigo de verificacion, en minutos. */
    private int codeTtlMinutes = 15;

    public int getCodeTtlMinutes() {
        return codeTtlMinutes;
    }

    public void setCodeTtlMinutes(int codeTtlMinutes) {
        this.codeTtlMinutes = codeTtlMinutes;
    }
}
