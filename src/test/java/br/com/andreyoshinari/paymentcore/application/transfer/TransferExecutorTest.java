package br.com.andreyoshinari.paymentcore.application.transfer;

import br.com.andreyoshinari.paymentcore.api.dto.request.TransferRequest;
import br.com.andreyoshinari.paymentcore.domain.entity.Account;
import br.com.andreyoshinari.paymentcore.domain.entity.AccountTransaction;
import br.com.andreyoshinari.paymentcore.domain.entity.Transfer;
import br.com.andreyoshinari.paymentcore.domain.entity.User;
import br.com.andreyoshinari.paymentcore.domain.enums.AccountStatus;
import br.com.andreyoshinari.paymentcore.domain.enums.AccountTransactionType;
import br.com.andreyoshinari.paymentcore.domain.exception.BusinessException;
import br.com.andreyoshinari.paymentcore.domain.exception.IdempotencyConflictException;
import br.com.andreyoshinari.paymentcore.domain.exception.ResourceNotFoundException;
import br.com.andreyoshinari.paymentcore.domain.repository.AccountRepository;
import br.com.andreyoshinari.paymentcore.domain.repository.AccountTransactionRepository;
import br.com.andreyoshinari.paymentcore.domain.repository.TransferRepository;
import br.com.andreyoshinari.paymentcore.security.AuthenticatedUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TransferExecutorTest {

	private static final UUID USER_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
	private static final UUID LOW_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
	private static final UUID HIGH_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");

	@Mock
	private AccountRepository accountRepository;

	@Mock
	private TransferRepository transferRepository;

	@Mock
	private AccountTransactionRepository accountTransactionRepository;

	@Mock
	private AuthenticatedUser authenticatedUser;

	@InjectMocks
	private TransferExecutor transferExecutor;

	private Account sender;
	private Account recipient;

	@BeforeEach
	void setUp() {
		sender = account(HIGH_ID, "111111111111", new BigDecimal("100.00"), AccountStatus.ACTIVE);
		recipient = account(LOW_ID, "222222222222", new BigDecimal("0.00"), AccountStatus.ACTIVE);
	}

	@Test
	void transfersAndLocksAccountsInDeterministicOrder() {
		when(authenticatedUser.id()).thenReturn(USER_ID);
		when(transferRepository.findByIdempotencyKey("key-1")).thenReturn(Optional.empty());
		when(accountRepository.findIdByUserId(USER_ID)).thenReturn(Optional.of(HIGH_ID));
		when(accountRepository.findIdByAccountNumber("222222222222")).thenReturn(Optional.of(LOW_ID));
		when(accountRepository.findByIdForUpdate(LOW_ID)).thenReturn(Optional.of(recipient));
		when(accountRepository.findByIdForUpdate(HIGH_ID)).thenReturn(Optional.of(sender));

		TransferOutcome outcome = transferExecutor.execute("key-1", request("222222222222", "30.00", "Lunch"));

		InOrder locks = inOrder(accountRepository);
		locks.verify(accountRepository).findByIdForUpdate(LOW_ID);
		locks.verify(accountRepository).findByIdForUpdate(HIGH_ID);
		assertThat(sender.getBalance()).isEqualByComparingTo("70.00");
		assertThat(recipient.getBalance()).isEqualByComparingTo("30.00");
		assertThat(outcome.created()).isTrue();
		assertThat(outcome.response().senderAccountNumber()).isEqualTo("111111111111");
		assertThat(outcome.response().recipientAccountNumber()).isEqualTo("222222222222");
		assertThat(outcome.response().amount()).isEqualByComparingTo("30.00");

		ArgumentCaptor<AccountTransaction> movements = ArgumentCaptor.forClass(AccountTransaction.class);
		verify(accountTransactionRepository, org.mockito.Mockito.times(2)).save(movements.capture());
		assertThat(movements.getAllValues())
				.extracting(AccountTransaction::getTransactionType)
				.containsExactly(AccountTransactionType.TRANSFER_SENT, AccountTransactionType.TRANSFER_RECEIVED);
	}

	@Test
	void rejectsInsufficientBalanceWithoutPersisting() {
		sender = account(HIGH_ID, "111111111111", new BigDecimal("10.00"), AccountStatus.ACTIVE);
		when(authenticatedUser.id()).thenReturn(USER_ID);
		when(transferRepository.findByIdempotencyKey("key-1")).thenReturn(Optional.empty());
		when(accountRepository.findIdByUserId(USER_ID)).thenReturn(Optional.of(HIGH_ID));
		when(accountRepository.findIdByAccountNumber("222222222222")).thenReturn(Optional.of(LOW_ID));
		when(accountRepository.findByIdForUpdate(LOW_ID)).thenReturn(Optional.of(recipient));
		when(accountRepository.findByIdForUpdate(HIGH_ID)).thenReturn(Optional.of(sender));

		assertThatThrownBy(() -> transferExecutor.execute("key-1", request("222222222222", "30.00", null)))
				.isInstanceOf(BusinessException.class)
				.hasFieldOrPropertyWithValue("code", "INSUFFICIENT_BALANCE");

		assertThat(sender.getBalance()).isEqualByComparingTo("10.00");
		verify(transferRepository, never()).saveAndFlush(any());
	}

	@Test
	void rejectsInactiveRecipient() {
		recipient = account(LOW_ID, "222222222222", new BigDecimal("0.00"), AccountStatus.BLOCKED);
		when(authenticatedUser.id()).thenReturn(USER_ID);
		when(transferRepository.findByIdempotencyKey("key-1")).thenReturn(Optional.empty());
		when(accountRepository.findIdByUserId(USER_ID)).thenReturn(Optional.of(HIGH_ID));
		when(accountRepository.findIdByAccountNumber("222222222222")).thenReturn(Optional.of(LOW_ID));
		when(accountRepository.findByIdForUpdate(LOW_ID)).thenReturn(Optional.of(recipient));
		when(accountRepository.findByIdForUpdate(HIGH_ID)).thenReturn(Optional.of(sender));

		assertThatThrownBy(() -> transferExecutor.execute("key-1", request("222222222222", "10.00", null)))
				.isInstanceOf(BusinessException.class)
				.hasFieldOrPropertyWithValue("code", "ACCOUNT_NOT_ACTIVE");
		verify(transferRepository, never()).saveAndFlush(any());
	}

	@Test
	void rejectsTransferToTheSameAccount() {
		when(authenticatedUser.id()).thenReturn(USER_ID);
		when(transferRepository.findByIdempotencyKey("key-1")).thenReturn(Optional.empty());
		when(accountRepository.findIdByUserId(USER_ID)).thenReturn(Optional.of(HIGH_ID));
		when(accountRepository.findIdByAccountNumber("111111111111")).thenReturn(Optional.of(HIGH_ID));

		assertThatThrownBy(() -> transferExecutor.execute("key-1", request("111111111111", "10.00", null)))
				.isInstanceOf(BusinessException.class)
				.hasFieldOrPropertyWithValue("code", "SAME_ACCOUNT");
		verify(accountRepository, never()).findByIdForUpdate(any());
	}

	@Test
	void returnsTheExistingTransferWhenTheIdempotencyKeyIsReplayed() {
		Transfer existing = new Transfer(sender, recipient, new BigDecimal("30.00"), "Lunch", "key-1");
		when(authenticatedUser.id()).thenReturn(USER_ID);
		when(transferRepository.findByIdempotencyKey("key-1")).thenReturn(Optional.of(existing));
		when(accountRepository.findIdByUserId(USER_ID)).thenReturn(Optional.of(HIGH_ID));
		when(accountRepository.findIdByAccountNumber("222222222222")).thenReturn(Optional.of(LOW_ID));

		TransferOutcome outcome = transferExecutor.execute(" key-1 ", request("222222222222", "30.00", " Lunch "));

		assertThat(outcome.created()).isFalse();
		assertThat(outcome.response().amount()).isEqualByComparingTo("30.00");
		verify(accountRepository, never()).findByIdForUpdate(any());
		verify(transferRepository, never()).saveAndFlush(any());
	}

	@Test
	void rejectsAReplayedKeyWithDifferentData() {
		Transfer existing = new Transfer(sender, recipient, new BigDecimal("30.00"), "Lunch", "key-1");
		when(authenticatedUser.id()).thenReturn(USER_ID);
		when(transferRepository.findByIdempotencyKey("key-1")).thenReturn(Optional.of(existing));
		when(accountRepository.findIdByUserId(USER_ID)).thenReturn(Optional.of(HIGH_ID));
		when(accountRepository.findIdByAccountNumber("222222222222")).thenReturn(Optional.of(LOW_ID));

		assertThatThrownBy(() -> transferExecutor.execute("key-1", request("222222222222", "10.00", "Lunch")))
				.isInstanceOf(IdempotencyConflictException.class);
		verify(accountRepository, never()).findByIdForUpdate(any());
	}

	@Test
	void rejectsAnUnknownRecipientAccount() {
		when(authenticatedUser.id()).thenReturn(USER_ID);
		when(transferRepository.findByIdempotencyKey("key-1")).thenReturn(Optional.empty());
		when(accountRepository.findIdByUserId(USER_ID)).thenReturn(Optional.of(HIGH_ID));
		when(accountRepository.findIdByAccountNumber("999999999999")).thenReturn(Optional.empty());

		assertThatThrownBy(() -> transferExecutor.execute("key-1", request("999999999999", "10.00", null)))
				.isInstanceOf(ResourceNotFoundException.class)
				.hasMessage("Recipient account not found");
		verify(accountRepository, never()).findByIdForUpdate(any());
		verify(transferRepository, never()).saveAndFlush(any());
	}

	private TransferRequest request(String recipientAccountNumber, String amount, String description) {
		return new TransferRequest(recipientAccountNumber, new BigDecimal(amount), description);
	}

	private Account account(UUID id, String accountNumber, BigDecimal balance, AccountStatus status) {
		Account account = new Account(new User("Ana Silva", "ana@example.com", "hash"), accountNumber);
		ReflectionTestUtils.setField(account, "id", id);
		ReflectionTestUtils.setField(account, "balance", balance);
		ReflectionTestUtils.setField(account, "status", status);
		return account;
	}

}
