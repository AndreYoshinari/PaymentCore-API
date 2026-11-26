package br.com.andreyoshinari.paymentcore.domain.entity;

import br.com.andreyoshinari.paymentcore.domain.enums.TransferStatus;
import br.com.andreyoshinari.paymentcore.domain.exception.ConflictException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "transfers")
public class Transfer {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	@Column(name = "id", nullable = false, updatable = false)
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "sender_account_id", nullable = false, updatable = false)
	private Account senderAccount;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "recipient_account_id", nullable = false, updatable = false)
	private Account recipientAccount;

	@Column(name = "amount", nullable = false, precision = 19, scale = 2)
	private BigDecimal amount;

	@Column(name = "description", length = 255)
	private String description;

	@Enumerated(EnumType.STRING)
	@Column(name = "status", nullable = false, length = 20)
	private TransferStatus status = TransferStatus.COMPLETED;

	@Column(name = "idempotency_key", nullable = false, unique = true, updatable = false, length = 100)
	private String idempotencyKey;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	@Column(name = "reversed_at")
	private Instant reversedAt;

	protected Transfer() {
	}

	public Transfer(
			Account senderAccount,
			Account recipientAccount,
			BigDecimal amount,
			String description,
			String idempotencyKey) {
		this.senderAccount = Objects.requireNonNull(senderAccount, "senderAccount");
		this.recipientAccount = Objects.requireNonNull(recipientAccount, "recipientAccount");
		this.amount = Objects.requireNonNull(amount, "amount");
		this.description = description;
		this.idempotencyKey = Objects.requireNonNull(idempotencyKey, "idempotencyKey");
	}

	@PrePersist
	void onCreate() {
		Instant now = Instant.now();
		createdAt = now;
		updatedAt = now;
	}

	@PreUpdate
	void onUpdate() {
		updatedAt = Instant.now();
	}

	public void reverse(Instant reversedAt) {
		if (status != TransferStatus.COMPLETED) {
			throw new ConflictException("TRANSFER_NOT_REVERSIBLE", "Transfer cannot be reversed");
		}
		this.reversedAt = Objects.requireNonNull(reversedAt, "reversedAt");
		this.status = TransferStatus.REVERSED;
	}

	public boolean matches(UUID senderAccountId, UUID recipientAccountId, BigDecimal amount, String description) {
		return senderAccount.getId().equals(senderAccountId)
				&& recipientAccount.getId().equals(recipientAccountId)
				&& this.amount.compareTo(amount) == 0
				&& Objects.equals(this.description, description);
	}

	public UUID getId() {
		return id;
	}

	public Account getSenderAccount() {
		return senderAccount;
	}

	public Account getRecipientAccount() {
		return recipientAccount;
	}

	public BigDecimal getAmount() {
		return amount;
	}

	public String getDescription() {
		return description;
	}

	public TransferStatus getStatus() {
		return status;
	}

	public String getIdempotencyKey() {
		return idempotencyKey;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}

	public Instant getReversedAt() {
		return reversedAt;
	}

}
