package br.com.andreyoshinari.paymentcore.domain.entity;

import br.com.andreyoshinari.paymentcore.domain.enums.AccountTransactionType;
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
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "account_transactions")
public class AccountTransaction {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	@Column(name = "id", nullable = false, updatable = false)
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "account_id", nullable = false, updatable = false)
	private Account account;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "transfer_id", nullable = false, updatable = false)
	private Transfer transfer;

	@Enumerated(EnumType.STRING)
	@Column(name = "transaction_type", nullable = false, length = 30)
	private AccountTransactionType transactionType;

	@Column(name = "amount", nullable = false, precision = 19, scale = 2)
	private BigDecimal amount;

	@Column(name = "description", length = 255)
	private String description;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	protected AccountTransaction() {
	}

	public AccountTransaction(
			Account account,
			Transfer transfer,
			AccountTransactionType transactionType,
			BigDecimal amount,
			String description) {
		this.account = Objects.requireNonNull(account, "account");
		this.transfer = Objects.requireNonNull(transfer, "transfer");
		this.transactionType = Objects.requireNonNull(transactionType, "transactionType");
		this.amount = Objects.requireNonNull(amount, "amount");
		this.description = description;
	}

	@PrePersist
	void onCreate() {
		createdAt = Instant.now();
	}

	public UUID getId() {
		return id;
	}

	public Account getAccount() {
		return account;
	}

	public Transfer getTransfer() {
		return transfer;
	}

	public AccountTransactionType getTransactionType() {
		return transactionType;
	}

	public BigDecimal getAmount() {
		return amount;
	}

	public String getDescription() {
		return description;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

}
