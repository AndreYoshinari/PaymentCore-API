package br.com.andreyoshinari.paymentcore.api.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Access token for protected operations")
public record LoginResponse(
		@Schema(description = "JWT access token. Send it as Authorization: Bearer <token>.")
		String accessToken,
		@Schema(description = "Token scheme", example = "Bearer")
		String tokenType,
		@Schema(description = "Lifetime of the token, in seconds", example = "900")
		long expiresIn
) {
}
