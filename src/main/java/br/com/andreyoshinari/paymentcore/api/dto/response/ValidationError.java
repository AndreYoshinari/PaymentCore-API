package br.com.andreyoshinari.paymentcore.api.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "One field that failed validation. The rejected value is not included.")
public record ValidationError(
		String field,
		String message
) {
}
