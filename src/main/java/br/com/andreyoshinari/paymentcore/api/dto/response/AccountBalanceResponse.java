package br.com.andreyoshinari.paymentcore.api.dto.response;

import br.com.andreyoshinari.paymentcore.domain.enums.AccountStatus;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.util.UUID;

@Schema(description = "Current balance of the authenticated account")
public record AccountBalanceResponse(
		UUID accountId,
		String accountNumber,
		BigDecimal balance,
		String currency,
		AccountStatus status
) {
}
