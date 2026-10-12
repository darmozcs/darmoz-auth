package com.darmoz.auth.service;

import com.darmoz.auth.config.EmailVerificationProperties;
import com.darmoz.auth.entity.EmailVerificationToken;
import com.darmoz.auth.entity.User;
import com.darmoz.auth.exception.ConflictException;
import com.darmoz.auth.exception.InvalidVerificationCodeException;
import com.darmoz.auth.exception.TooManyRequestsException;
import com.darmoz.auth.repository.EmailVerificationTokenRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class EmailVerificationService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final int CODE_BOUND = 1_000_000; // exclusivo -> 6 digitos, 000000-999999

    private final EmailVerificationTokenRepository tokenRepository;
    private final EmailVerificationProperties properties;
    private final DarmozMailClient darmozMailClient;
    private final TransactionTemplate requiresNewTransaction;

    public EmailVerificationService(EmailVerificationTokenRepository tokenRepository,
                                     EmailVerificationProperties properties,
                                     DarmozMailClient darmozMailClient,
                                     PlatformTransactionManager transactionManager) {
        this.tokenRepository = tokenRepository;
        this.properties = properties;
        this.darmozMailClient = darmozMailClient;
        this.requiresNewTransaction = new TransactionTemplate(transactionManager);
        this.requiresNewTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /**
     * Genera un codigo nuevo, invalida (consume) cualquier codigo activo previo del usuario,
     * persiste el hash y llama a darmoz-mail para enviarlo. Todo dentro de la misma
     * transaccion: si el envio falla, la excepcion propaga y Spring hace rollback de los
     * INSERT/UPDATE — nunca queda "un codigo en DB" sin que se haya mandado el mail
     * correspondiente.
     * <p>
     * Throttle: si el ultimo codigo del usuario se emitio hace menos de
     * resendCooldownSeconds, rechaza con {@link TooManyRequestsException} (429).
     */
    @Transactional
    public void generateAndSend(User user) {
        if (user.isEmailVerified()) {
            throw new ConflictException("El email ya esta verificado");
        }

        OffsetDateTime now = OffsetDateTime.now();
        Optional<EmailVerificationToken> latest = tokenRepository.findFirstByUserIdOrderByCreatedAtDesc(user.getId());
        if (latest.isPresent()
                && latest.get().getCreatedAt().isAfter(now.minusSeconds(properties.getResendCooldownSeconds()))) {
            throw new TooManyRequestsException(
                    "Ya se envio un codigo hace instantes; espera " + properties.getResendCooldownSeconds()
                            + " segundos antes de pedir otro");
        }

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
     * Valida el codigo contra el codigo activo del usuario (sin consumir, sin expirar) y lo marca
     * consumido. No muta User (email_verified / unverified_login_count): eso es
     * responsabilidad del caller (AuthService).
     * <p>
     * Un codigo incorrecto suma un intento fallido al codigo activo; al llegar a
     * maxFailedAttempts el codigo se consume y hay que pedir uno nuevo. Ese contador se persiste
     * en una transaccion propia (REQUIRES_NEW): la transaccion del caller hace rollback por la
     * excepcion que se lanza a continuacion, y el intento fallido tiene que sobrevivir igual.
     */
    @Transactional
    public void confirm(User user, String rawCode) {
        if (user.isEmailVerified()) {
            throw new ConflictException("El email ya esta verificado");
        }

        OffsetDateTime now = OffsetDateTime.now();
        EmailVerificationToken token = tokenRepository
                .findFirstByUserIdAndConsumedAtIsNullAndExpiresAtAfterOrderByCreatedAtDesc(user.getId(), now)
                .orElseThrow(() -> new InvalidVerificationCodeException("Codigo invalido o expirado"));

        if (!MessageDigest.isEqual(token.getCodeHash().getBytes(StandardCharsets.UTF_8),
                hash(rawCode).getBytes(StandardCharsets.UTF_8))) {
            boolean exhausted = registerFailedAttempt(token.getId());
            if (exhausted) {
                throw new InvalidVerificationCodeException(
                        "Codigo invalido; se agotaron los intentos, solicita un codigo nuevo");
            }
            throw new InvalidVerificationCodeException("Codigo invalido o expirado");
        }

        token.consume();
        tokenRepository.save(token);
    }

    private boolean registerFailedAttempt(UUID tokenId) {
        Boolean exhausted = requiresNewTransaction.execute(status -> tokenRepository.findById(tokenId)
                .map(token -> {
                    boolean result = token.registerFailedAttempt(properties.getMaxFailedAttempts());
                    tokenRepository.save(token);
                    return result;
                })
                .orElse(true));
        return Boolean.TRUE.equals(exhausted);
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
