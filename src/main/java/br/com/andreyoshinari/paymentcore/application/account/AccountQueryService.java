package br.com.andreyoshinari.paymentcore.application.account;

import br.com.andreyoshinari.paymentcore.api.dto.response.AccountBalanceResponse;
import br.com.andreyoshinari.paymentcore.api.dto.response.AccountResponse;
import br.com.andreyoshinari.paymentcore.api.dto.response.AccountTransactionResponse;
import br.com.andreyoshinari.paymentcore.api.dto.response.PageResponse;
import br.com.andreyoshinari.paymentcore.domain.entity.Account;
import br.com.andreyoshinari.paymentcore.domain.entity.AccountTransaction;
import br.com.andreyoshinari.paymentcore.domain.exception.BusinessException;
import br.com.andreyoshinari.paymentcore.domain.exception.ResourceNotFoundException;
import br.com.andreyoshinari.paymentcore.domain.repository.AccountRepository;
import br.com.andreyoshinari.paymentcore.domain.repository.AccountTransactionRepository;
import br.com.andreyoshinari.paymentcore.security.AuthenticatedUser;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
public class AccountQueryService {

	static final int MAX_PAGE_SIZE = 100;

	private final AccountRepository accountRepository;
	private final AccountTransactionRepository accountTransactionRepository;
	private final AuthenticatedUser authenticatedUser;

	public AccountQueryService(
			AccountRepository accountRepository,
			AccountTransactionRepository accountTransactionRepository,
			AuthenticatedUser authenticatedUser) {
		this.accountRepository = accountRepository;
		this.accountTransactionRepository = accountTransactionRepository;
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

	@Transactional(readOnly = true)
	public PageResponse<AccountTransactionResponse> getCurrentTransactions(Instant from, Instant to, Pageable pageable) {
		if (from != null && to != null && from.isAfter(to)) {
			throw new BusinessException("INVALID_PERIOD", "The start of the period must not be after the end");
		}
		Account account = loadCurrentAccount();
		Page<AccountTransaction> page = findPage(account.getId(), from, to, ordered(pageable));
		return new PageResponse<>(
				page.getContent().stream().map(AccountMapper::toTransaction).toList(),
				page.getNumber(),
				page.getSize(),
				page.getTotalElements(),
				page.getTotalPages());
	}

	private Page<AccountTransaction> findPage(UUID accountId, Instant from, Instant to, Pageable pageable) {
		if (from != null && to != null) {
			return accountTransactionRepository
					.findByAccount_IdAndCreatedAtGreaterThanEqualAndCreatedAtLessThanEqual(accountId, from, to, pageable);
		}
		if (from != null) {
			return accountTransactionRepository.findByAccount_IdAndCreatedAtGreaterThanEqual(accountId, from, pageable);
		}
		if (to != null) {
			return accountTransactionRepository.findByAccount_IdAndCreatedAtLessThanEqual(accountId, to, pageable);
		}
		return accountTransactionRepository.findByAccount_Id(accountId, pageable);
	}

	private static Pageable ordered(Pageable pageable) {
		int size = Math.min(pageable.getPageSize(), MAX_PAGE_SIZE);
		return PageRequest.of(pageable.getPageNumber(), size, Sort.by(Sort.Direction.DESC, "createdAt"));
	}

	private Account loadCurrentAccount() {
		return accountRepository.findByUserId(authenticatedUser.id())
				.orElseThrow(() -> new ResourceNotFoundException("Account not found"));
	}

}
