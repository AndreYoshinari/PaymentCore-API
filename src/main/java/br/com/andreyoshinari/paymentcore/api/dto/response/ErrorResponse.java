package br.com.andreyoshinari.paymentcore.api.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.http.HttpStatus;

import java.time.Instant;
import java.util.List;

@Schema(description = "Error returned to the client. Stack traces and rejected secrets are omitted.")
public record ErrorResponse(
		Instant timestamp,
		int status,
		String code,
		String message,
		String path,
		@JsonInclude(JsonInclude.Include.NON_NULL) List<ValidationError> errors
) {

	public static ErrorResponse of(HttpStatus status, String code, String message, String path) {
		return of(status, code, message, path, null);
	}

	public static ErrorResponse of(
			HttpStatus status,
			String code,
			String message,
			String path,
			List<ValidationError> errors) {
		List<ValidationError> visibleErrors = errors == null || errors.isEmpty() ? null : List.copyOf(errors);
		return new ErrorResponse(Instant.now(), status.value(), code, message, path, visibleErrors);
	}

}
