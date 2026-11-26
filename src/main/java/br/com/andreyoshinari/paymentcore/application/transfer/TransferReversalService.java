package br.com.andreyoshinari.paymentcore.application.transfer;

import br.com.andreyoshinari.paymentcore.api.dto.response.TransferResponse;
import br.com.andreyoshinari.paymentcore.domain.entity.Account;
import br.com.andreyoshinari.paymentcore.domain.entity.AccountTransaction;
import br.com.andreyoshinari.paymentcore.domain.entity.Transfer;
import br.com.andreyoshinari.paymentcore.domain.enums.AccountTransactionType;
import br.com.andreyoshinari.paymentcore.domain.enums.TransferStatus;
import br.com.andreyoshinari.paymentcore.domain.exception.ConflictException;
import br.com.andreyoshinari.paymentcore.domain.exception.ForbiddenException;
import br.com.andreyoshinari.paymentcore.domain.exception.ResourceNotFoundException;
import br.com.andreyoshinari.paymentcore.domain.repository.AccountRepository;
import br.com.andreyoshinari.paymentcore.domain.repository.AccountTransactionRepository;
import br.com.andreyoshinari.paymentcore.domain.repository.TransferRepository;
import br.com.andreyoshinari.paymentcore.security.AuthenticatedUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * Full reversal of a simulated transfer.
 *
 * <p>These rules belong to this study project. They do not reproduce PIX,
 * card networks, or any other real payment scheme.
 *
 * <p>Only the owner of the original source account can reverse the transfer.
 * The full amount is debited from the destination and credited back to the source.
 * There is no partial reversal. The destination must cover the full amount.
 * Account status does not block the reversal.
 * The original transfer is kept, marked {@code REVERSED}, and {@code reversed_at} is set.
 * The same transfer cannot be reversed twice.
 * The transfer row is locked first, then the two accounts in identifier order,
 * so two concurrent reversals cannot both succeed.
 */
@Service
public class TransferReversalService {

	private final TransferRepository transferRepository;
	private final AccountRepository accountRepository;
	private final AccountTransactionRepository accountTransactionRepository;
	private final AuthenticatedUser authenticatedUser;

	public TransferReversalService(
			TransferRepository transferRepository,
			AccountRepository accountRepository,
			AccountTransactionRepository accountTransactionRepository,
			AuthenticatedUser authenticatedUser) {
		this.transferRepository = transferRepository;
		this.accountRepository = accountRepository;
		this.accountTransactionRepository = accountTransactionRepository;
		this.authenticatedUser = authenticatedUser;
	}

	@Transactional
	public TransferResponse reverse(UUID transferId) {
		Transfer transfer = transferRepository.findByIdForUpdate(transferId)
				.orElseThrow(() -> new ResourceNotFoundException("Transfer not found"));
		UUID currentAccountId = accountRepository.findIdByUserId(authenticatedUser.id())
				.orElseThrow(() -> new ResourceNotFoundException("Account not found"));
		if (!transfer.getSenderAccount().getId().equals(currentAccountId)) {
			throw new ForbiddenException();
		}
		if (transfer.getStatus() != TransferStatus.COMPLETED) {
			throw new ConflictException("TRANSFER_NOT_REVERSIBLE", "Transfer cannot be reversed");
		}

		LockedAccounts accounts = lockAccounts(transfer.getSenderAccount().getId(), transfer.getRecipientAccount().getId());
		if (accounts.recipient().getBalance().compareTo(transfer.getAmount()) < 0) {
			throw new ConflictException("INSUFFICIENT_BALANCE", "Insufficient balance");
		}
		accounts.recipient().debit(transfer.getAmount());
		accounts.sender().credit(transfer.getAmount());
		transfer.reverse(Instant.now());
		accountTransactionRepository.save(movement(accounts.recipient(), transfer, AccountTransactionType.REVERSAL_SENT));
		accountTransactionRepository.save(movement(accounts.sender(), transfer, AccountTransactionType.REVERSAL_RECEIVED));
		return toResponse(transfer);
	}

	private LockedAccounts lockAccounts(UUID senderId, UUID recipientId) {
		UUID firstId = senderId.compareTo(recipientId) <= 0 ? senderId : recipientId;
		UUID secondId = firstId.equals(senderId) ? recipientId : senderId;
		Account first = lock(firstId);
		Account second = lock(secondId);
		Account sender = first.getId().equals(senderId) ? first : second;
		Account recipient = sender == first ? second : first;
		return new LockedAccounts(sender, recipient);
	}

	private Account lock(UUID accountId) {
		return accountRepository.findByIdForUpdate(accountId)
				.orElseThrow(() -> new ResourceNotFoundException("Account not found"));
	}

	private static AccountTransaction movement(Account account, Transfer transfer, AccountTransactionType type) {
		return new AccountTransaction(account, transfer, type, transfer.getAmount(), transfer.getDescription());
	}

	private static TransferResponse toResponse(Transfer transfer) {
		return new TransferResponse(
				transfer.getId(),
				transfer.getSenderAccount().getAccountNumber(),
				transfer.getRecipientAccount().getAccountNumber(),
				transfer.getAmount(),
				transfer.getDescription(),
				transfer.getStatus(),
				transfer.getCreatedAt(),
				transfer.getReversedAt());
	}

	private record LockedAccounts(Account sender, Account recipient) {
	}

}
