package br.com.andreyoshinari.paymentcore.api.exception;

import br.com.andreyoshinari.paymentcore.api.dto.response.ErrorResponse;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.webmvc.error.ErrorController;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ApiErrorController implements ErrorController {

	private static final Logger log = LoggerFactory.getLogger(ApiErrorController.class);

	@RequestMapping("/error")
	public ResponseEntity<ErrorResponse> handleError(HttpServletRequest request) {
		HttpStatus status = status(request);
		if (status.is5xxServerError()) {
			log.error("Unexpected error", (Throwable) request.getAttribute(RequestDispatcher.ERROR_EXCEPTION));
		}
		String path = (String) request.getAttribute(RequestDispatcher.ERROR_REQUEST_URI);
		if (path == null) {
			path = request.getRequestURI();
		}
		ErrorResponse body = ErrorResponse.of(status, code(status), message(status), path);
		return ResponseEntity.status(status).body(body);
	}

	private HttpStatus status(HttpServletRequest request) {
		Object statusCode = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
		if (statusCode instanceof Integer code) {
			HttpStatus status = HttpStatus.resolve(code);
			if (status != null) {
				return status;
			}
		}
		return HttpStatus.INTERNAL_SERVER_ERROR;
	}

	private String code(HttpStatus status) {
		if (status.is5xxServerError()) {
			return ApiErrorCode.INTERNAL_ERROR.name();
		}
		if (status.value() == 422) {
			return ApiErrorCode.BUSINESS_RULE_VIOLATION.name();
		}
		return switch (status) {
			case BAD_REQUEST -> ApiErrorCode.INVALID_REQUEST.name();
			case UNAUTHORIZED -> ApiErrorCode.AUTHENTICATION_FAILED.name();
			case FORBIDDEN -> ApiErrorCode.ACCESS_DENIED.name();
			case NOT_FOUND -> ApiErrorCode.RESOURCE_NOT_FOUND.name();
			case CONFLICT -> "CONFLICT";
			default -> status.name();
		};
	}

	private String message(HttpStatus status) {
		if (status.value() == 422) {
			return "Business rule violation";
		}
		return switch (status) {
			case BAD_REQUEST -> "Invalid request";
			case UNAUTHORIZED -> "Authentication failed";
			case FORBIDDEN -> "Access denied";
			case NOT_FOUND -> "Resource not found";
			case CONFLICT -> "Request conflict";
			default -> status.is5xxServerError()
					? "An unexpected error occurred"
					: "Request could not be processed";
		};
	}

}
