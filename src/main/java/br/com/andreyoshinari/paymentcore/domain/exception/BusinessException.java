package br.com.andreyoshinari.paymentcore.domain.exception;

import java.util.Objects;

public class BusinessException extends RuntimeException {

	private final String code;

	public BusinessException(String message) {
		this("BUSINESS_RULE_VIOLATION", message);
	}

	public BusinessException(String code, String message) {
		super(Objects.requireNonNull(message, "message"));
		this.code = Objects.requireNonNull(code, "code");
	}

	public String getCode() {
		return code;
	}

}
