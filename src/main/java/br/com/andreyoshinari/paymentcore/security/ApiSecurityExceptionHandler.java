package br.com.andreyoshinari.paymentcore.security;

import br.com.andreyoshinari.paymentcore.api.dto.response.ErrorResponse;
import br.com.andreyoshinari.paymentcore.api.exception.ApiErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Component
public class ApiSecurityExceptionHandler implements AuthenticationEntryPoint, AccessDeniedHandler {

	private final JsonMapper jsonMapper;

	public ApiSecurityExceptionHandler(JsonMapper jsonMapper) {
		this.jsonMapper = jsonMapper;
	}

	@Override
	public void commence(
			HttpServletRequest request,
			HttpServletResponse response,
			AuthenticationException authException) throws IOException {
		response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
		write(response, HttpStatus.UNAUTHORIZED, ApiErrorCode.AUTHENTICATION_FAILED, "Authentication failed", request);
	}

	@Override
	public void handle(
			HttpServletRequest request,
			HttpServletResponse response,
			AccessDeniedException accessDeniedException) throws IOException {
		write(response, HttpStatus.FORBIDDEN, ApiErrorCode.ACCESS_DENIED, "Access denied", request);
	}

	private void write(
			HttpServletResponse response,
			HttpStatus status,
			ApiErrorCode code,
			String message,
			HttpServletRequest request) throws IOException {
		response.setStatus(status.value());
		response.setCharacterEncoding(StandardCharsets.UTF_8.name());
		response.setContentType(MediaType.APPLICATION_JSON_VALUE);
		jsonMapper.writeValue(
				response.getOutputStream(),
				ErrorResponse.of(status, code.name(), message, request.getRequestURI()));
	}

}
