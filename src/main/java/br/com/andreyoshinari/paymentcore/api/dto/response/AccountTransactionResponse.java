package br.com.andreyoshinari.paymentcore.api.dto.response;

import br.com.andreyoshinari.paymentcore.domain.enums.AccountTransactionType;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Schema(description = "One statement movement. The type indicates the direction.")
public record AccountTransactionResponse(
		UUID id,
		AccountTransactionType transactionType,
		BigDecimal amount,
		String description,
		Instant createdAt,
		UUID transferId
) {
}
