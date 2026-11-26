package br.com.andreyoshinari.paymentcore.application.transfer;

import br.com.andreyoshinari.paymentcore.domain.entity.Account;
import br.com.andreyoshinari.paymentcore.domain.entity.AccountTransaction;
import br.com.andreyoshinari.paymentcore.domain.entity.Transfer;
import br.com.andreyoshinari.paymentcore.domain.entity.User;
import br.com.andreyoshinari.paymentcore.domain.enums.AccountTransactionType;
import br.com.andreyoshinari.paymentcore.domain.enums.TransferStatus;
import br.com.andreyoshinari.paymentcore.domain.exception.ConflictException;
import br.com.andreyoshinari.paymentcore.domain.exception.ForbiddenException;
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
class TransferReversalServiceTest {

	private static final UUID USER_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
	private static final UUID TRANSFER_ID = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
	private static final UUID LOW_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
	private static final UUID HIGH_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");

	@Mock
	private TransferRepository transferRepository;

	@Mock
	private AccountRepository accountRepository;

	@Mock
	private AccountTransactionRepository accountTransactionRepository;

	@Mock
	private AuthenticatedUser authenticatedUser;

	@InjectMocks
	private TransferReversalService transferReversalService;

	private Account sender;
	private Account recipient;
	private Transfer transfer;

	@BeforeEach
	void setUp() {
		sender = account(HIGH_ID, "111111111111", "70.00");
		recipient = account(LOW_ID, "222222222222", "30.00");
		transfer = new Transfer(sender, recipient, new BigDecimal("30.00"), "Lunch", "key-1");
		ReflectionTestUtils.setField(transfer, "id", TRANSFER_ID);
	}

	@Test
	void returnsTheFullAmountAndLocksTheTransferBeforeTheAccounts() {
		when(authenticatedUser.id()).thenReturn(USER_ID);
		when(transferRepository.findByIdForUpdate(TRANSFER_ID)).thenReturn(Optional.of(transfer));
		when(accountRepository.findIdByUserId(USER_ID)).thenReturn(Optional.of(HIGH_ID));
		when(accountRepository.findByIdForUpdate(LOW_ID)).thenReturn(Optional.of(recipient));
		when(accountRepository.findByIdForUpdate(HIGH_ID)).thenReturn(Optional.of(sender));

		var response = transferReversalService.reverse(TRANSFER_ID);

		InOrder locks = inOrder(transferRepository, accountRepository);
		locks.verify(transferRepository).findByIdForUpdate(TRANSFER_ID);
		locks.verify(accountRepository).findByIdForUpdate(LOW_ID);
		locks.verify(accountRepository).findByIdForUpdate(HIGH_ID);
		assertThat(sender.getBalance()).isEqualByComparingTo("100.00");
		assertThat(recipient.getBalance()).isEqualByComparingTo("0.00");
		assertThat(transfer.getStatus()).isEqualTo(TransferStatus.REVERSED);
		assertThat(transfer.getReversedAt()).isNotNull();
		assertThat(response.status()).isEqualTo(TransferStatus.REVERSED);
		assertThat(response.reversedAt()).isNotNull();

		ArgumentCaptor<AccountTransaction> movements = ArgumentCaptor.forClass(AccountTransaction.class);
		verify(accountTransactionRepository, org.mockito.Mockito.times(2)).save(movements.capture());
		assertThat(movements.getAllValues())
				.extracting(AccountTransaction::getTransactionType)
				.containsExactly(AccountTransactionType.REVERSAL_SENT, AccountTransactionType.REVERSAL_RECEIVED);
	}

	@Test
	void rejectsAUserWhoDoesNotOwnTheSourceAccount() {
		when(authenticatedUser.id()).thenReturn(USER_ID);
		when(transferRepository.findByIdForUpdate(TRANSFER_ID)).thenReturn(Optional.of(transfer));
		when(accountRepository.findIdByUserId(USER_ID)).thenReturn(Optional.of(LOW_ID));

		assertThatThrownBy(() -> transferReversalService.reverse(TRANSFER_ID))
				.isInstanceOf(ForbiddenException.class);
		assertThat(transfer.getStatus()).isEqualTo(TransferStatus.COMPLETED);
		verify(accountRepository, never()).findByIdForUpdate(any());
	}

	@Test
	void rejectsASecondReversal() {
		ReflectionTestUtils.setField(transfer, "status", TransferStatus.REVERSED);
		when(authenticatedUser.id()).thenReturn(USER_ID);
		when(transferRepository.findByIdForUpdate(TRANSFER_ID)).thenReturn(Optional.of(transfer));
		when(accountRepository.findIdByUserId(USER_ID)).thenReturn(Optional.of(HIGH_ID));

		assertThatThrownBy(() -> transferReversalService.reverse(TRANSFER_ID))
				.isInstanceOf(ConflictException.class)
				.hasFieldOrPropertyWithValue("code", "TRANSFER_NOT_REVERSIBLE");
		verify(accountRepository, never()).findByIdForUpdate(any());
	}

	@Test
	void rejectsTheReversalWhenTheDestinationCannotCoverTheAmount() {
		ReflectionTestUtils.setField(recipient, "balance", new BigDecimal("10.00"));
		when(authenticatedUser.id()).thenReturn(USER_ID);
		when(transferRepository.findByIdForUpdate(TRANSFER_ID)).thenReturn(Optional.of(transfer));
		when(accountRepository.findIdByUserId(USER_ID)).thenReturn(Optional.of(HIGH_ID));
		when(accountRepository.findByIdForUpdate(LOW_ID)).thenReturn(Optional.of(recipient));
		when(accountRepository.findByIdForUpdate(HIGH_ID)).thenReturn(Optional.of(sender));

		assertThatThrownBy(() -> transferReversalService.reverse(TRANSFER_ID))
				.isInstanceOf(ConflictException.class)
				.hasFieldOrPropertyWithValue("code", "INSUFFICIENT_BALANCE");
		assertThat(transfer.getStatus()).isEqualTo(TransferStatus.COMPLETED);
		assertThat(transfer.getReversedAt()).isNull();
		assertThat(sender.getBalance()).isEqualByComparingTo("70.00");
		assertThat(recipient.getBalance()).isEqualByComparingTo("10.00");
	}

	@Test
	void rejectsAnUnknownTransfer() {
		when(transferRepository.findByIdForUpdate(TRANSFER_ID)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> transferReversalService.reverse(TRANSFER_ID))
				.isInstanceOf(ResourceNotFoundException.class)
				.hasMessage("Transfer not found");
		verify(accountRepository, never()).findByIdForUpdate(any());
	}

	private Account account(UUID id, String accountNumber, String balance) {
		Account account = new Account(new User("Ana Silva", accountNumber + "@example.com", "hash"), accountNumber);
		ReflectionTestUtils.setField(account, "id", id);
		ReflectionTestUtils.setField(account, "balance", new BigDecimal(balance));
		return account;
	}

}
