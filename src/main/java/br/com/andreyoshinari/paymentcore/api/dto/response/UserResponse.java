package br.com.andreyoshinari.paymentcore.api.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

@Schema(description = "Public user data. The password is never returned.")
public record UserResponse(
		UUID id,
		String name,
		String email,
		Instant createdAt
) {
}
