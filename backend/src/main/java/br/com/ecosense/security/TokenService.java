package br.com.ecosense.security;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.stereotype.Service;

import com.nimbusds.jose.jwk.source.ImmutableSecret;

import br.com.ecosense.config.AuthProperties;
import br.com.ecosense.persistence.UserEntity;

/**
 * Emite e valida os tokens de acesso (JWT assinado com HMAC-SHA256).
 * O token leva o id do usuário (sub), o perfil e a versão do token (ver); o front o envia no
 * cabeçalho {@code Authorization: Bearer <token>}. Não há sessão no servidor.
 */
@Service
public class TokenService {

    private static final Logger log = LoggerFactory.getLogger(TokenService.class);
    static final String ISSUER = "ecosense";

    public record IssuedToken(String value, Instant expiresAt) {}

    private final AuthProperties props;
    private final Clock clock;
    private final JwtEncoder encoder;
    private final JwtDecoder decoder;

    public TokenService(AuthProperties props, Clock clock) {
        this.props = props;
        this.clock = clock;
        SecretKey key = key(props.jwtSecret());
        this.encoder = new NimbusJwtEncoder(new ImmutableSecret<>(key));
        NimbusJwtDecoder nimbus = NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
        nimbus.setJwtValidator(JwtValidators.createDefaultWithIssuer(ISSUER)); // validade + emissor
        this.decoder = nimbus;
    }

    public IssuedToken issue(UserEntity user) {
        Instant now = clock.instant().truncatedTo(ChronoUnit.SECONDS);
        Instant expiresAt = now.plus(props.tokenTtl());
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(ISSUER)
                .subject(String.valueOf(user.getId()))
                .issuedAt(now)
                .expiresAt(expiresAt)
                .claim("role", user.getRole())
                .claim("ver", user.getTokenVersion())
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return new IssuedToken(encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue(), expiresAt);
    }

    /** Usado pelo Spring Security para validar assinatura e validade dos tokens recebidos. */
    public JwtDecoder decoder() {
        return decoder;
    }

    private static SecretKey key(String secret) {
        byte[] bytes;
        if (secret == null || secret.isBlank()) {
            bytes = new byte[32];
            new SecureRandom().nextBytes(bytes);
            log.warn("JWT_SECRET não definido: usando uma chave aleatória. Os logins valem até o back-end "
                    + "reiniciar. Defina JWT_SECRET no .env (mínimo 32 caracteres) para manter as sessões.");
        } else {
            bytes = secret.getBytes(StandardCharsets.UTF_8);
            if (bytes.length < 32) {
                throw new IllegalStateException("JWT_SECRET precisa ter pelo menos 32 caracteres.");
            }
        }
        return new SecretKeySpec(bytes, "HmacSHA256");
    }
}
