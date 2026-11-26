package br.com.andreyoshinari.paymentcore.api.exception;

import br.com.andreyoshinari.paymentcore.api.dto.response.ErrorResponse;
import br.com.andreyoshinari.paymentcore.api.dto.response.ValidationError;
import br.com.andreyoshinari.paymentcore.domain.exception.BusinessException;
import br.com.andreyoshinari.paymentcore.domain.exception.ConflictException;
import br.com.andreyoshinari.paymentcore.domain.exception.EmailAlreadyRegisteredException;
import br.com.andreyoshinari.paymentcore.domain.exception.ForbiddenException;
import br.com.andreyoshinari.paymentcore.domain.exception.IdempotencyConflictException;
import br.com.andreyoshinari.paymentcore.domain.exception.ResourceNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.ArrayList;
import java.util.List;

@RestControllerAdvice
public class ApiExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<ErrorResponse> handleMethodArgumentNotValid(
			MethodArgumentNotValidException exception,
			HttpServletRequest request) {
		return validation(fieldErrors(exception.getBindingResult().getFieldErrors(), exception.getBindingResult().getGlobalErrors()), request);
	}

	@ExceptionHandler(HandlerMethodValidationException.class)
	public ResponseEntity<ErrorResponse> handleHandlerMethodValidation(
			HandlerMethodValidationException exception,
			HttpServletRequest request) {
		List<ValidationError> errors = new ArrayList<>();
		exception.getParameterValidationResults().forEach(result -> result.getResolvableErrors().forEach(error -> {
			String field = result.getMethodParameter().getParameterName();
			if (error instanceof FieldError fieldError) {
				field = fieldError.getField();
			}
			errors.add(new ValidationError(field == null ? "request" : field, text(error.getDefaultMessage())));
		}));
		exception.getCrossParameterValidationResults()
				.forEach(error -> errors.add(new ValidationError("request", text(error.getDefaultMessage()))));
		return validation(errors, request);
	}

	@ExceptionHandler(ConstraintViolationException.class)
	public ResponseEntity<ErrorResponse> handleConstraintViolation(
			ConstraintViolationException exception,
			HttpServletRequest request) {
		List<ValidationError> errors = exception.getConstraintViolations().stream()
				.map(this::toValidationError)
				.toList();
		return validation(errors, request);
	}

	@ExceptionHandler(HttpMessageNotReadableException.class)
	public ResponseEntity<ErrorResponse> handleUnreadableMessage(
			HttpMessageNotReadableException exception,
			HttpServletRequest request) {
		return respond(HttpStatus.BAD_REQUEST, ApiErrorCode.MALFORMED_REQUEST, "Malformed request body", request);
	}

	@ExceptionHandler(MethodArgumentTypeMismatchException.class)
	public ResponseEntity<ErrorResponse> handleTypeMismatch(
			MethodArgumentTypeMismatchException exception,
			HttpServletRequest request) {
		String message = "Invalid value for parameter '" + exception.getName() + "'";
		return respond(HttpStatus.BAD_REQUEST, ApiErrorCode.INVALID_PARAMETER, message, request);
	}

	@ExceptionHandler(ResourceNotFoundException.class)
	public ResponseEntity<ErrorResponse> handleResourceNotFound(
			ResourceNotFoundException exception,
			HttpServletRequest request) {
		return respond(HttpStatus.NOT_FOUND, ApiErrorCode.RESOURCE_NOT_FOUND, exception.getMessage(), request);
	}

	@ExceptionHandler(EmailAlreadyRegisteredException.class)
	public ResponseEntity<ErrorResponse> handleEmailAlreadyRegistered(
			EmailAlreadyRegisteredException exception,
			HttpServletRequest request) {
		return respond(HttpStatus.CONFLICT, ApiErrorCode.EMAIL_ALREADY_REGISTERED, exception.getMessage(), request);
	}

	@ExceptionHandler(IdempotencyConflictException.class)
	public ResponseEntity<ErrorResponse> handleIdempotencyConflict(
			IdempotencyConflictException exception,
			HttpServletRequest request) {
		return respond(HttpStatus.CONFLICT, ApiErrorCode.IDEMPOTENCY_CONFLICT, exception.getMessage(), request);
	}

	@ExceptionHandler(ConflictException.class)
	public ResponseEntity<ErrorResponse> handleConflict(ConflictException exception, HttpServletRequest request) {
		return respond(HttpStatus.CONFLICT, exception.getCode(), exception.getMessage(), request);
	}

	@ExceptionHandler(ForbiddenException.class)
	public ResponseEntity<ErrorResponse> handleForbidden(ForbiddenException exception, HttpServletRequest request) {
		return respond(HttpStatus.FORBIDDEN, ApiErrorCode.ACCESS_DENIED, exception.getMessage(), request);
	}

	@ExceptionHandler(BusinessException.class)
	public ResponseEntity<ErrorResponse> handleBusiness(BusinessException exception, HttpServletRequest request) {
		return respond(HttpStatus.UNPROCESSABLE_CONTENT, exception.getCode(), exception.getMessage(), request);
	}

	@ExceptionHandler(AuthenticationException.class)
	public ResponseEntity<ErrorResponse> handleAuthentication(
			AuthenticationException exception,
			HttpServletRequest request) {
		return respond(HttpStatus.UNAUTHORIZED, ApiErrorCode.AUTHENTICATION_FAILED, "Authentication failed", request);
	}

	@ExceptionHandler(AccessDeniedException.class)
	public ResponseEntity<ErrorResponse> handleAccessDenied(
			AccessDeniedException exception,
			HttpServletRequest request) {
		return respond(HttpStatus.FORBIDDEN, ApiErrorCode.ACCESS_DENIED, "Access denied", request);
	}

	// Keeps client errors on their HTTP status and hides unexpected failures.
	@ExceptionHandler(Exception.class)
	public ResponseEntity<ErrorResponse> handleUnexpected(Exception exception, HttpServletRequest request) {
		if (exception instanceof org.springframework.web.ErrorResponse errorResponse) {
			HttpStatus status = httpStatus(errorResponse.getStatusCode().value());
			if (status.is5xxServerError()) {
				log.error("Unexpected error", exception);
			}
			return respond(status, codeFor(status), messageFor(status), request);
		}
		log.error("Unexpected error", exception);
		return respond(HttpStatus.INTERNAL_SERVER_ERROR, ApiErrorCode.INTERNAL_ERROR, "An unexpected error occurred", request);
	}

	private ResponseEntity<ErrorResponse> validation(List<ValidationError> errors, HttpServletRequest request) {
		return respond(HttpStatus.BAD_REQUEST, ApiErrorCode.VALIDATION_ERROR.name(), "Validation failed", request, errors);
	}

	private ResponseEntity<ErrorResponse> respond(
			HttpStatus status,
			ApiErrorCode code,
			String message,
			HttpServletRequest request) {
		return respond(status, code.name(), message, request, List.of());
	}

	private ResponseEntity<ErrorResponse> respond(
			HttpStatus status,
			String code,
			String message,
			HttpServletRequest request) {
		return respond(status, code, message, request, List.of());
	}

	private ResponseEntity<ErrorResponse> respond(
			HttpStatus status,
			String code,
			String message,
			HttpServletRequest request,
			List<ValidationError> errors) {
		ErrorResponse body = ErrorResponse.of(status, code, message, request.getRequestURI(), errors);
		return ResponseEntity.status(status).body(body);
	}

	private List<ValidationError> fieldErrors(List<FieldError> fieldErrors, List<ObjectError> globalErrors) {
		List<ValidationError> errors = new ArrayList<>();
		fieldErrors.forEach(error -> errors.add(new ValidationError(error.getField(), text(error.getDefaultMessage()))));
		globalErrors.forEach(error -> errors.add(new ValidationError(error.getObjectName(), text(error.getDefaultMessage()))));
		return errors;
	}

	private ValidationError toValidationError(ConstraintViolation<?> violation) {
		String field = violation.getPropertyPath() == null ? "request" : violation.getPropertyPath().toString();
		if (field.isBlank()) {
			field = "request";
		}
		return new ValidationError(field, text(violation.getMessage()));
	}

	private String text(String message) {
		return message == null || message.isBlank() ? "Invalid value" : message;
	}

	private HttpStatus httpStatus(int statusCode) {
		HttpStatus status = HttpStatus.resolve(statusCode);
		return status == null ? HttpStatus.INTERNAL_SERVER_ERROR : status;
	}

	private String codeFor(HttpStatus status) {
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
			default -> status.name();
		};
	}

	private String messageFor(HttpStatus status) {
		if (status.value() == 422) {
			return "Business rule violation";
		}
		return switch (status) {
			case BAD_REQUEST -> "Invalid request";
			case UNAUTHORIZED -> "Authentication failed";
			case FORBIDDEN -> "Access denied";
			case NOT_FOUND -> "Resource not found";
			default -> status.is5xxServerError()
					? "An unexpected error occurred"
					: "Request could not be processed";
		};
	}

}
