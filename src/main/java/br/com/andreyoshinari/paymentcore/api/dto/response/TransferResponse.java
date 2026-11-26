package br.com.andreyoshinari.paymentcore.api.dto.response;

import br.com.andreyoshinari.paymentcore.domain.enums.TransferStatus;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Schema(description = "Simulated transfer. reversedAt is present only after a reversal.")
public record TransferResponse(
		UUID id,
		String senderAccountNumber,
		String recipientAccountNumber,
		BigDecimal amount,
		String description,
		TransferStatus status,
		Instant createdAt,
		Instant reversedAt
) {
}
