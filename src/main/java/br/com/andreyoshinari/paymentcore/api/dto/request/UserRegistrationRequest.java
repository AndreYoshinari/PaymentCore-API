package br.com.andreyoshinari.paymentcore.api.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UserRegistrationRequest(
		@Schema(description = "Display name", example = "Ana Silva", maxLength = 120)
		@NotBlank
		@Size(max = 120)
		String name,

		@Schema(description = "Email used to log in", example = "ana@example.com", maxLength = 254)
		@NotBlank
		@Email
		@Size(max = 254)
		String email,

		@Schema(description = "Password from 8 to 72 characters", format = "password", accessMode = Schema.AccessMode.WRITE_ONLY, minLength = 8, maxLength = 72)
		@NotBlank
		@Size(min = 8, max = 72)
		String password
) {
}
