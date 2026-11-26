package br.com.andreyoshinari.paymentcore.security.jwt;

import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

@Component
public class JwtTokenProvider {

	public static final String TOKEN_TYPE = "Bearer";

	private final JwtEncoder jwtEncoder;
	private final JwtProperties properties;

	public JwtTokenProvider(JwtEncoder jwtEncoder, JwtProperties properties) {
		this.jwtEncoder = jwtEncoder;
		this.properties = properties;
	}

	public IssuedToken issue(UUID userId) {
		Instant issuedAt = Instant.now();
		Instant expiresAt = issuedAt.plus(properties.expiration());
		JwtClaimsSet claims = JwtClaimsSet.builder()
				.issuer("payment-core")
				.subject(userId.toString())
				.issuedAt(issuedAt)
				.expiresAt(expiresAt)
				.build();
		JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
		String value = jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
		return new IssuedToken(value, properties.expiration());
	}

}
