package br.com.andreyoshinari.paymentcore.api.dto.response;

import br.com.andreyoshinari.paymentcore.domain.enums.AccountStatus;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Schema(description = "Account of the authenticated user")
public record AccountResponse(
		UUID id,
		String accountNumber,
		BigDecimal balance,
		String currency,
		AccountStatus status,
		Instant createdAt
) {
}
