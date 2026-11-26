package br.com.andreyoshinari.paymentcore.api.controller;

import br.com.andreyoshinari.paymentcore.api.dto.request.TransferRequest;
import br.com.andreyoshinari.paymentcore.api.dto.response.ErrorResponse;
import br.com.andreyoshinari.paymentcore.api.dto.response.TransferResponse;
import br.com.andreyoshinari.paymentcore.application.transfer.TransferOutcome;
import br.com.andreyoshinari.paymentcore.application.transfer.TransferReversalService;
import br.com.andreyoshinari.paymentcore.application.transfer.TransferService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Tag(name = "Transfers")
@Validated
@RestController
@RequestMapping("/api/v1/transfers")
public class TransferController {

	private final TransferService transferService;
	private final TransferReversalService transferReversalService;

	public TransferController(TransferService transferService, TransferReversalService transferReversalService) {
		this.transferService = transferService;
		this.transferReversalService = transferReversalService;
	}

	@Operation(
			summary = "Transfer money to another account",
			description = """
					Debits the authenticated user's account and credits the recipient.
					The same Idempotency-Key with the same data returns the original transfer.
					The same key with different data returns 409.
					""",
			responses = {
					@ApiResponse(responseCode = "201", description = "Transfer created"),
					@ApiResponse(responseCode = "200", description = "Idempotency key replayed with the same data"),
					@ApiResponse(responseCode = "400", description = "Invalid body, amount, or Idempotency-Key",
							content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
					@ApiResponse(responseCode = "401", description = "Missing or invalid token",
							content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
					@ApiResponse(responseCode = "404", description = "Source or recipient account was not found",
							content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
					@ApiResponse(responseCode = "409", description = "Idempotency key already used with different data",
							content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
					@ApiResponse(responseCode = "422", description = "Same account, inactive account, or insufficient balance",
							content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
			})
	@PostMapping
	public ResponseEntity<TransferResponse> transfer(
			@Parameter(name = "Idempotency-Key", in = ParameterIn.HEADER, required = true,
					description = "Unique key for this transfer attempt. Maximum 100 characters.")
			@RequestHeader("Idempotency-Key") @NotBlank @Size(max = 100) String idempotencyKey,
			@Valid @RequestBody TransferRequest request) {
		TransferOutcome outcome = transferService.transfer(idempotencyKey, request);
		HttpStatus status = outcome.created() ? HttpStatus.CREATED : HttpStatus.OK;
		return ResponseEntity.status(status).body(outcome.response());
	}

	@Operation(
			summary = "Reverse a completed transfer",
			description = """
					Only the owner of the original source account can reverse the transfer.
					The full amount returns to that account and is debited from the destination.
					The original transfer is kept and marked REVERSED. A second reversal returns 409.
					This is a study rule and does not reproduce PIX.
					""",
			responses = {
					@ApiResponse(responseCode = "200", description = "Transfer reversed"),
					@ApiResponse(responseCode = "401", description = "Missing or invalid token",
							content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
					@ApiResponse(responseCode = "403", description = "The user does not own the source account",
							content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
					@ApiResponse(responseCode = "404", description = "Transfer was not found",
							content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
					@ApiResponse(responseCode = "409", description = "Already reversed, or the destination cannot cover the amount",
							content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
			})
	@PostMapping("/{transferId}/reverse")
	public TransferResponse reverse(
			@Parameter(description = "Transfer to reverse") @PathVariable UUID transferId) {
		return transferReversalService.reverse(transferId);
	}

}
