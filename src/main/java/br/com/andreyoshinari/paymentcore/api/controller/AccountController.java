package br.com.andreyoshinari.paymentcore.api.controller;

import br.com.andreyoshinari.paymentcore.api.dto.response.AccountBalanceResponse;
import br.com.andreyoshinari.paymentcore.api.dto.response.AccountResponse;
import br.com.andreyoshinari.paymentcore.api.dto.response.ErrorResponse;
import br.com.andreyoshinari.paymentcore.application.account.AccountQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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

}
