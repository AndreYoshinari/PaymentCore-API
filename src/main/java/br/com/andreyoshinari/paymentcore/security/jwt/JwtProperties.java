package br.com.andreyoshinari.paymentcore.security.jwt;

import org.springframework.boot.context.properties.ConfigurationProperties;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

@ConfigurationProperties(prefix = "security.jwt")
public record JwtProperties(String secret, Duration expiration) {

	public JwtProperties {
		if (secret == null || secret.isBlank()) {
			throw new IllegalArgumentException("JWT_SECRET must be set");
		}
		if (secret.getBytes(StandardCharsets.UTF_8).length < 32) {
			throw new IllegalArgumentException("JWT_SECRET must be at least 32 bytes");
		}
		if (expiration == null || expiration.isZero() || expiration.isNegative()) {
			throw new IllegalArgumentException("JWT expiration must be positive");
		}
	}

	public SecretKey secretKey() {
		return new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
	}

}
