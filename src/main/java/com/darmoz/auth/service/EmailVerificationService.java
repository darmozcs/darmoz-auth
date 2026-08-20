package com.darmoz.auth.service;

import com.darmoz.auth.config.EmailVerificationProperties;
import com.darmoz.auth.entity.EmailVerificationToken;
import com.darmoz.auth.entity.User;
import com.darmoz.auth.exception.ConflictException;
import com.darmoz.auth.exception.InvalidVerificationCodeException;
import com.darmoz.auth.repository.EmailVerificationTokenRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.List;

@Service
public class EmailVerificationService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final int CODE_BOUND = 1_000_000; // exclusivo -> 6 digitos, 000000-999999

    private final EmailVerificationTokenRepository tokenRepository;
    private final EmailVerificationProperties properties;
    private final DarmozMailClient darmozMailClient;

    public EmailVerificationService(EmailVerificationTokenRepository tokenRepository,
                                     EmailVerificationProperties properties,
                                     DarmozMailClient darmozMailClient) {
        this.tokenRepository = tokenRepository;
        this.properties = properties;
        this.darmozMailClient = darmozMailClient;
    }

    /**
     * Genera un codigo nuevo, invalida (consume) cualquier codigo activo previo del usuario,
     * persiste el hash y llama a darmoz-mail para enviarlo. Todo dentro de la misma
     * transaccion: si el envio falla, la excepcion propaga y Spring hace rollback de los
     * INSERT/UPDATE — nunca queda "un codigo en DB" sin que se haya mandado el mail
     * correspondiente.
     */
    @Transactional
    public void generateAndSend(User user) {
        if (user.isEmailVerified()) {
            throw new ConflictException("El email ya esta verificado");
        }

        OffsetDateTime now = OffsetDateTime.now();
        List<EmailVerificationToken> active =
                tokenRepository.findAllByUserIdAndConsumedAtIsNullAndExpiresAtAfter(user.getId(), now);
        active.forEach(EmailVerificationToken::consume);
        tokenRepository.saveAll(active);

        String rawCode = generateCode();
        OffsetDateTime expiresAt = now.plusMinutes(properties.getCodeTtlMinutes());
        tokenRepository.save(new EmailVerificationToken(user.getId(), hash(rawCode), expiresAt));

        darmozMailClient.sendVerificationCode(user.getEmail(), rawCode);
    }

    /**
     * Valida el codigo contra el hash almacenado (sin consumir, sin expirar) y lo marca
     * consumido. No muta User (email_verified / unverified_login_count): eso es
     * responsabilidad del caller (AuthService).
     */
    @Transactional
    public void confirm(User user, String rawCode) {
        if (user.isEmailVerified()) {
            throw new ConflictException("El email ya esta verificado");
        }

        OffsetDateTime now = OffsetDateTime.now();
        EmailVerificationToken token = tokenRepository
                .findByUserIdAndCodeHashAndConsumedAtIsNullAndExpiresAtAfter(user.getId(), hash(rawCode), now)
                .orElseThrow(() -> new InvalidVerificationCodeException("Codigo invalido o expirado"));
        token.consume();
        tokenRepository.save(token);
    }

    private String generateCode() {
        return String.format("%06d", SECURE_RANDOM.nextInt(CODE_BOUND));
    }

    private String hash(String rawCode) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(rawCode.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hashed);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 no disponible", e);
        }
    }
}
