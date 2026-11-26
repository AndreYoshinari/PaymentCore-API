package br.com.andreyoshinari.paymentcore.application.account;

import br.com.andreyoshinari.paymentcore.api.dto.response.AccountBalanceResponse;
import br.com.andreyoshinari.paymentcore.api.dto.response.AccountResponse;
import br.com.andreyoshinari.paymentcore.domain.entity.Account;
import br.com.andreyoshinari.paymentcore.domain.exception.ResourceNotFoundException;
import br.com.andreyoshinari.paymentcore.domain.repository.AccountRepository;
import br.com.andreyoshinari.paymentcore.security.AuthenticatedUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountQueryService {

	private final AccountRepository accountRepository;
	private final AuthenticatedUser authenticatedUser;

	public AccountQueryService(AccountRepository accountRepository, AuthenticatedUser authenticatedUser) {
		this.accountRepository = accountRepository;
		this.authenticatedUser = authenticatedUser;
	}

	@Transactional(readOnly = true)
	public AccountResponse getCurrentAccount() {
		return AccountMapper.toAccount(loadCurrentAccount());
	}

	@Transactional(readOnly = true)
	public AccountBalanceResponse getCurrentBalance() {
		return AccountMapper.toBalance(loadCurrentAccount());
	}

	private Account loadCurrentAccount() {
		return accountRepository.findByUserId(authenticatedUser.id())
				.orElseThrow(() -> new ResourceNotFoundException("Account not found"));
	}

}
