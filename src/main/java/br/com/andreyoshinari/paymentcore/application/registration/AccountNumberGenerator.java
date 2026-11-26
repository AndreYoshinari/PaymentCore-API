package br.com.andreyoshinari.paymentcore.application.registration;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;

@Component
public class AccountNumberGenerator {

	static final int LENGTH = 12;

	private final SecureRandom random = new SecureRandom();

	public String generate() {
		StringBuilder accountNumber = new StringBuilder(LENGTH);
		accountNumber.append(random.nextInt(9) + 1);
		for (int index = 1; index < LENGTH; index++) {
			accountNumber.append(random.nextInt(10));
		}
		return accountNumber.toString();
	}

}
