package br.com.andreyoshinari.paymentcore.domain.exception;

import java.util.Objects;

public class ResourceNotFoundException extends RuntimeException {

	public ResourceNotFoundException(String message) {
		super(Objects.requireNonNull(message, "message"));
	}

}
