package br.com.andreyoshinari.paymentcore.api.controller;

import br.com.andreyoshinari.paymentcore.api.dto.request.UserRegistrationRequest;
import br.com.andreyoshinari.paymentcore.api.dto.response.ErrorResponse;
import br.com.andreyoshinari.paymentcore.api.dto.response.RegistrationResponse;
import br.com.andreyoshinari.paymentcore.application.registration.UserRegistrationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Authentication")
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

	private final UserRegistrationService userRegistrationService;

	public AuthController(UserRegistrationService userRegistrationService) {
		this.userRegistrationService = userRegistrationService;
	}

	@Operation(summary = "Register a user and an account", security = {}, responses = {
			@ApiResponse(responseCode = "201", description = "User and one active BRL account were created"),
			@ApiResponse(responseCode = "400", description = "Invalid or malformed body",
					content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
			@ApiResponse(responseCode = "409", description = "Email is already registered",
					content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
	})
	@PostMapping("/register")
	public ResponseEntity<RegistrationResponse> register(@Valid @RequestBody UserRegistrationRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED).body(userRegistrationService.register(request));
	}

}
