package br.com.andreyoshinari.paymentcore.domain.entity;

import br.com.andreyoshinari.paymentcore.domain.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AccountTest {

	@Test
	void rejectsAMovementThatWouldMakeTheBalanceNegative() {
		Account account = account("10.00");

		assertThatThrownBy(() -> account.debit(new BigDecimal("10.01")))
				.isInstanceOf(BusinessException.class)
				.hasFieldOrPropertyWithValue("code", "INSUFFICIENT_BALANCE");

		assertThat(account.getBalance()).isEqualByComparingTo("10.00");
	}

	@Test
	void rejectsNonPositiveAmounts() {
		Account account = account("10.00");

		assertThatThrownBy(() -> account.debit(new BigDecimal("0.00")))
				.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> account.credit(new BigDecimal("-1.00")))
				.isInstanceOf(IllegalArgumentException.class);

		assertThat(account.getBalance()).isEqualByComparingTo("10.00");
	}

	@Test
	void creditIncreasesTheBalance() {
		Account account = account("10.00");

		account.credit(new BigDecimal("2.50"));

		assertThat(account.getBalance()).isEqualByComparingTo("12.50");
	}

	private Account account(String balance) {
		Account account = new Account(new User("Ana Silva", "ana@example.com", "hash"), "123456789012");
		ReflectionTestUtils.setField(account, "balance", new BigDecimal(balance));
		return account;
	}

}
