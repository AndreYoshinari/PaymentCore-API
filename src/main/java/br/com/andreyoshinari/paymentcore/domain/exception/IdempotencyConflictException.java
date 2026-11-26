package br.com.andreyoshinari.paymentcore.domain.exception;

public class IdempotencyConflictException extends RuntimeException {

	public IdempotencyConflictException() {
		super("Idempotency key has already been used with different data");
	}

}
