package br.com.andreyoshinari.paymentcore.api.controller;

import br.com.andreyoshinari.paymentcore.api.dto.response.AccountBalanceResponse;
import br.com.andreyoshinari.paymentcore.api.dto.response.AccountResponse;
import br.com.andreyoshinari.paymentcore.api.dto.response.AccountTransactionResponse;
import br.com.andreyoshinari.paymentcore.api.dto.response.ErrorResponse;
import br.com.andreyoshinari.paymentcore.api.dto.response.PageResponse;
import br.com.andreyoshinari.paymentcore.application.account.AccountQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

@Tag(name = "Accounts")
@RestController
@RequestMapping("/api/v1/accounts")
public class AccountController {

	private final AccountQueryService accountQueryService;

	public AccountController(AccountQueryService accountQueryService) {
		this.accountQueryService = accountQueryService;
	}

	@Operation(summary = "Read the authenticated account", responses = {
			@ApiResponse(responseCode = "200", description = "Account of the token subject"),
			@ApiResponse(responseCode = "401", description = "Missing or invalid token",
					content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
			@ApiResponse(responseCode = "404", description = "The user has no account",
					content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
	})
	@GetMapping("/me")
	public AccountResponse me() {
		return accountQueryService.getCurrentAccount();
	}

	@Operation(summary = "Read the authenticated account balance", responses = {
			@ApiResponse(responseCode = "200", description = "Current balance"),
			@ApiResponse(responseCode = "401", description = "Missing or invalid token",
					content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
			@ApiResponse(responseCode = "404", description = "The user has no account",
					content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
	})
	@GetMapping("/me/balance")
	public AccountBalanceResponse balance() {
		return accountQueryService.getCurrentBalance();
	}

	@Operation(
			summary = "Read the authenticated account statement",
			description = """
					Returns one page of movements for the authenticated account, newest first.
					An account id supplied by the client is ignored.
					page starts at 0. size defaults to 20 and is capped at 100.
					from and to are optional, inclusive, and use ISO-8601.
					""",
			responses = {
					@ApiResponse(responseCode = "200", description = "One page of movements"),
					@ApiResponse(responseCode = "400", description = "Invalid date",
							content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
					@ApiResponse(responseCode = "401", description = "Missing or invalid token",
							content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
					@ApiResponse(responseCode = "404", description = "The user has no account",
							content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
					@ApiResponse(responseCode = "422", description = "from is after to",
							content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
			})
	@GetMapping("/me/transactions")
	public PageResponse<AccountTransactionResponse> transactions(
			@Parameter(description = "Inclusive start, ISO-8601", example = "2026-01-01T00:00:00Z")
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
			@Parameter(description = "Inclusive end, ISO-8601", example = "2026-01-31T23:59:59Z")
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
			@PageableDefault(size = 20) Pageable pageable) {
		return accountQueryService.getCurrentTransactions(from, to, pageable);
	}

}
