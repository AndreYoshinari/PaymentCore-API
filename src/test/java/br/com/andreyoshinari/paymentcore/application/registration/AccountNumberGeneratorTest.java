package br.com.andreyoshinari.paymentcore.application.registration;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AccountNumberGeneratorTest {

	@Test
	void generatesNumericAccountNumberWithinColumnLimit() {
		String accountNumber = new AccountNumberGenerator().generate();

		assertThat(accountNumber).hasSize(AccountNumberGenerator.LENGTH);
		assertThat(accountNumber).matches("[1-9][0-9]{11}");
	}

}
