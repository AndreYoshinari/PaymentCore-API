package br.com.andreyoshinari.paymentcore.application.account;

import br.com.andreyoshinari.paymentcore.api.dto.response.AccountBalanceResponse;
import br.com.andreyoshinari.paymentcore.api.dto.response.AccountResponse;
import br.com.andreyoshinari.paymentcore.api.dto.response.AccountTransactionResponse;
import br.com.andreyoshinari.paymentcore.domain.entity.Account;
import br.com.andreyoshinari.paymentcore.domain.entity.AccountTransaction;

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

	static AccountTransactionResponse toTransaction(AccountTransaction transaction) {
		return new AccountTransactionResponse(
				transaction.getId(),
				transaction.getTransactionType(),
				transaction.getAmount(),
				transaction.getDescription(),
				transaction.getCreatedAt(),
				transaction.getTransfer().getId());
	}

}
