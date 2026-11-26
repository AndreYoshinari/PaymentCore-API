package br.com.andreyoshinari.paymentcore.api.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "User and the account created with the registration")
public record RegistrationResponse(
		UserResponse user,
		AccountResponse account
) {
}
