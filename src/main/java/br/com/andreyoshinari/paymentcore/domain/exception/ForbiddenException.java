package br.com.andreyoshinari.paymentcore.domain.exception;

public class ForbiddenException extends RuntimeException {

	public ForbiddenException() {
		super("Access denied");
	}

}
