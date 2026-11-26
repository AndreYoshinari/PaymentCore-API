package br.com.andreyoshinari.paymentcore.application.account;

import br.com.andreyoshinari.paymentcore.api.dto.response.AccountResponse;
import br.com.andreyoshinari.paymentcore.domain.entity.Account;
import br.com.andreyoshinari.paymentcore.domain.entity.User;
import br.com.andreyoshinari.paymentcore.domain.enums.AccountStatus;
import br.com.andreyoshinari.paymentcore.domain.exception.BusinessException;
import br.com.andreyoshinari.paymentcore.domain.exception.ResourceNotFoundException;
import br.com.andreyoshinari.paymentcore.domain.repository.AccountRepository;
import br.com.andreyoshinari.paymentcore.domain.repository.AccountTransactionRepository;
import br.com.andreyoshinari.paymentcore.security.AuthenticatedUser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccountQueryServiceTest {

	@Mock
	private AccountRepository accountRepository;

	@Mock
	private AccountTransactionRepository accountTransactionRepository;

	@Mock
	private AuthenticatedUser authenticatedUser;

	@InjectMocks
	private AccountQueryService accountQueryService;

	@Test
	void loadsTheAccountOfTheAuthenticatedUser() {
		UUID userId = UUID.randomUUID();
		User user = new User("Ana Silva", "ana@example.com", "$2a$10$hash");
		Account account = new Account(user, "123456789012");
		when(authenticatedUser.id()).thenReturn(userId);
		when(accountRepository.findByUserId(userId)).thenReturn(Optional.of(account));

		AccountResponse response = accountQueryService.getCurrentAccount();

		assertThat(response.accountNumber()).isEqualTo("123456789012");
		assertThat(response.balance()).isEqualByComparingTo(new BigDecimal("0.00"));
		assertThat(response.currency()).isEqualTo("BRL");
		assertThat(response.status()).isEqualTo(AccountStatus.ACTIVE);
		verify(accountRepository).findByUserId(userId);
	}

	@Test
	void failsWhenTheAuthenticatedUserHasNoAccount() {
		UUID userId = UUID.randomUUID();
		when(authenticatedUser.id()).thenReturn(userId);
		when(accountRepository.findByUserId(userId)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> accountQueryService.getCurrentAccount())
				.isInstanceOf(ResourceNotFoundException.class)
				.hasMessage("Account not found");
	}

	@Test
	void loadsOnlyTheAuthenticatedAccountAndCapsThePage() {
		UUID userId = UUID.randomUUID();
		UUID accountId = UUID.randomUUID();
		Account account = new Account(new User("Ana Silva", "ana@example.com", "hash"), "123456789012");
		ReflectionTestUtils.setField(account, "id", accountId);
		when(authenticatedUser.id()).thenReturn(userId);
		when(accountRepository.findByUserId(userId)).thenReturn(Optional.of(account));
		when(accountTransactionRepository.findByAccount_Id(eq(accountId), any())).thenAnswer(invocation -> {
			Pageable pageable = invocation.getArgument(1);
			return new PageImpl<>(List.of(), pageable, 0);
		});

		var response = accountQueryService.getCurrentTransactions(
				null,
				null,
				PageRequest.of(2, 500, Sort.by("amount")));

		ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
		verify(accountTransactionRepository).findByAccount_Id(eq(accountId), pageable.capture());
		assertThat(pageable.getValue().getPageNumber()).isEqualTo(2);
		assertThat(pageable.getValue().getPageSize()).isEqualTo(AccountQueryService.MAX_PAGE_SIZE);
		assertThat(pageable.getValue().getSort().getOrderFor("createdAt").getDirection()).isEqualTo(Sort.Direction.DESC);
		assertThat(response.page()).isEqualTo(2);
		assertThat(response.size()).isEqualTo(AccountQueryService.MAX_PAGE_SIZE);
		assertThat(response.content()).isEmpty();
	}

	@Test
	void rejectsAPeriodThatEndsBeforeItStarts() {
		Instant from = Instant.parse("2026-01-03T00:00:00Z");
		Instant to = Instant.parse("2026-01-01T00:00:00Z");

		assertThatThrownBy(() -> accountQueryService.getCurrentTransactions(from, to, PageRequest.of(0, 20)))
				.isInstanceOf(BusinessException.class)
				.hasFieldOrPropertyWithValue("code", "INVALID_PERIOD");
		verifyNoInteractions(accountTransactionRepository);
	}

}
