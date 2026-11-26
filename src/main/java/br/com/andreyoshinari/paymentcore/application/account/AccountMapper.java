package br.com.andreyoshinari.paymentcore.application.account;

import br.com.andreyoshinari.paymentcore.api.dto.response.AccountBalanceResponse;
import br.com.andreyoshinari.paymentcore.api.dto.response.AccountResponse;
import br.com.andreyoshinari.paymentcore.domain.entity.Account;

final class AccountMapper {

	private AccountMapper() {
	}

	static AccountResponse toAccount(Account account) {
		return new AccountResponse(
				account.getId(),
				account.getAccountNumber(),
				account.getBalance(),
				account.getCurrency().trim(),
				account.getStatus(),
				account.getCreatedAt());
	}

	static AccountBalanceResponse toBalance(Account account) {
		return new AccountBalanceResponse(
				account.getId(),
				account.getAccountNumber(),
				account.getBalance(),
				account.getCurrency().trim(),
				account.getStatus());
	}

}
