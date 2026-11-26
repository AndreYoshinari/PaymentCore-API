package br.com.andreyoshinari.paymentcore.application.transfer;

import br.com.andreyoshinari.paymentcore.api.dto.request.TransferRequest;
import br.com.andreyoshinari.paymentcore.api.dto.response.TransferResponse;
import br.com.andreyoshinari.paymentcore.domain.entity.Account;
import br.com.andreyoshinari.paymentcore.domain.entity.AccountTransaction;
import br.com.andreyoshinari.paymentcore.domain.entity.Transfer;
import br.com.andreyoshinari.paymentcore.domain.enums.AccountStatus;
import br.com.andreyoshinari.paymentcore.domain.enums.AccountTransactionType;
import br.com.andreyoshinari.paymentcore.domain.exception.BusinessException;
import br.com.andreyoshinari.paymentcore.domain.exception.IdempotencyConflictException;
import br.com.andreyoshinari.paymentcore.domain.exception.ResourceNotFoundException;
import br.com.andreyoshinari.paymentcore.domain.repository.AccountRepository;
import br.com.andreyoshinari.paymentcore.domain.repository.AccountTransactionRepository;
import br.com.andreyoshinari.paymentcore.domain.repository.TransferRepository;
import br.com.andreyoshinari.paymentcore.security.AuthenticatedUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;

@Service
public class TransferExecutor {

	private final AccountRepository accountRepository;
	private final TransferRepository transferRepository;
	private final AccountTransactionRepository accountTransactionRepository;
	private final AuthenticatedUser authenticatedUser;

	public TransferExecutor(
			AccountRepository accountRepository,
			TransferRepository transferRepository,
			AccountTransactionRepository accountTransactionRepository,
			AuthenticatedUser authenticatedUser) {
		this.accountRepository = accountRepository;
		this.transferRepository = transferRepository;
		this.accountTransactionRepository = accountTransactionRepository;
		this.authenticatedUser = authenticatedUser;
	}

	@Transactional
	public TransferOutcome execute(String idempotencyKey, TransferRequest request) {
		String key = idempotencyKey.trim();
		BigDecimal amount = request.amount().setScale(2, RoundingMode.UNNECESSARY);
		String description = normalizeDescription(request.description());
		String recipientAccountNumber = request.recipientAccountNumber().trim();

		return transferRepository.findByIdempotencyKey(key)
				.map(existing -> resolveExisting(existing, amount, description, recipientAccountNumber))
				.orElseGet(() -> create(key, amount, description, recipientAccountNumber));
	}

	@Transactional(readOnly = true)
	public TransferOutcome replay(String idempotencyKey, TransferRequest request) {
		String key = idempotencyKey.trim();
		BigDecimal amount = request.amount().setScale(2, RoundingMode.UNNECESSARY);
		String description = normalizeDescription(request.description());
		Transfer transfer = transferRepository.findByIdempotencyKey(key)
				.orElseThrow(() -> new IdempotencyConflictException());
		return resolveExisting(transfer, amount, description, request.recipientAccountNumber().trim());
	}

	private TransferOutcome create(
			String idempotencyKey,
			BigDecimal amount,
			String description,
			String recipientAccountNumber) {
		UUID senderId = accountRepository.findIdByUserId(authenticatedUser.id())
				.orElseThrow(() -> new ResourceNotFoundException("Account not found"));
		UUID recipientId = accountRepository.findIdByAccountNumber(recipientAccountNumber)
				.orElseThrow(() -> new ResourceNotFoundException("Recipient account not found"));
		if (senderId.equals(recipientId)) {
			throw new BusinessException("SAME_ACCOUNT", "Source and recipient accounts must be different");
		}

		UUID firstId = senderId.compareTo(recipientId) <= 0 ? senderId : recipientId;
		UUID secondId = firstId.equals(senderId) ? recipientId : senderId;
		// Always lock the smaller id first so two transfers cannot deadlock on each other.
		Account first = lock(firstId);
		Account second = lock(secondId);
		Account sender = first.getId().equals(senderId) ? first : second;
		Account recipient = sender == first ? second : first;

		ensureActive(sender, "Source account is not active");
		ensureActive(recipient, "Recipient account is not active");
		sender.debit(amount);
		recipient.credit(amount);

		Transfer transfer = new Transfer(sender, recipient, amount, description, idempotencyKey);
		transferRepository.saveAndFlush(transfer);
		accountTransactionRepository.save(movement(sender, transfer, AccountTransactionType.TRANSFER_SENT, amount, description));
		accountTransactionRepository.save(movement(recipient, transfer, AccountTransactionType.TRANSFER_RECEIVED, amount, description));
		return new TransferOutcome(toResponse(transfer), true);
	}

	private TransferOutcome resolveExisting(
			Transfer transfer,
			BigDecimal amount,
			String description,
			String recipientAccountNumber) {
		UUID senderId = accountRepository.findIdByUserId(authenticatedUser.id())
				.orElseThrow(() -> new ResourceNotFoundException("Account not found"));
		UUID recipientId = accountRepository.findIdByAccountNumber(recipientAccountNumber)
				.orElseThrow(() -> new IdempotencyConflictException());
		if (!transfer.matches(senderId, recipientId, amount, description)) {
			throw new IdempotencyConflictException();
		}
		return new TransferOutcome(toResponse(transfer), false);
	}

	private Account lock(UUID accountId) {
		return accountRepository.findByIdForUpdate(accountId)
				.orElseThrow(() -> new ResourceNotFoundException("Account not found"));
	}

	private static void ensureActive(Account account, String message) {
		if (account.getStatus() != AccountStatus.ACTIVE) {
			throw new BusinessException("ACCOUNT_NOT_ACTIVE", message);
		}
	}

	private static AccountTransaction movement(
			Account account,
			Transfer transfer,
			AccountTransactionType type,
			BigDecimal amount,
			String description) {
		return new AccountTransaction(account, transfer, type, amount, description);
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

	private static String normalizeDescription(String description) {
		if (description == null || description.isBlank()) {
			return null;
		}
		return description.trim();
	}

}
