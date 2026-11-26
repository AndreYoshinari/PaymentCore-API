package br.com.andreyoshinari.paymentcore.api.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record TransferRequest(
		@Schema(description = "Destination account number", example = "123456789012", maxLength = 20)
		@NotBlank
		@Size(max = 20)
		String recipientAccountNumber,

		@Schema(description = "Positive amount with at most two decimal places", example = "10.00")
		@NotNull
		@DecimalMin(value = "0.01")
		@Digits(integer = 17, fraction = 2)
		BigDecimal amount,

		@Schema(description = "Optional note stored with the transfer and its statement entries", maxLength = 255)
		@Size(max = 255)
		String description
) {
}
