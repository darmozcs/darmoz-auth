package com.darmoz.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "email-verification")
public class EmailVerificationProperties {

    /** Vigencia del codigo de verificacion, en minutos. */
    private int codeTtlMinutes = 15;

    /** Intentos fallidos permitidos por codigo; al llegar a este numero el codigo se consume. */
    private int maxFailedAttempts = 5;

    /** Tiempo minimo entre dos envios de codigo al mismo usuario, en segundos. */
    private int resendCooldownSeconds = 60;

    public int getCodeTtlMinutes() {
        return codeTtlMinutes;
    }

    public void setCodeTtlMinutes(int codeTtlMinutes) {
        this.codeTtlMinutes = codeTtlMinutes;
    }

    public int getMaxFailedAttempts() {
        return maxFailedAttempts;
    }

    public void setMaxFailedAttempts(int maxFailedAttempts) {
        this.maxFailedAttempts = maxFailedAttempts;
    }

    public int getResendCooldownSeconds() {
        return resendCooldownSeconds;
    }

    public void setResendCooldownSeconds(int resendCooldownSeconds) {
        this.resendCooldownSeconds = resendCooldownSeconds;
    }
}
