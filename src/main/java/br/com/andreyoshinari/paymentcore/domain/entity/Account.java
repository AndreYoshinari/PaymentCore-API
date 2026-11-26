package br.com.andreyoshinari.paymentcore.domain.entity;

import br.com.andreyoshinari.paymentcore.domain.enums.AccountStatus;
import br.com.andreyoshinari.paymentcore.domain.exception.BusinessException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "accounts")
public class Account {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	@Column(name = "id", nullable = false, updatable = false)
	private UUID id;

	@OneToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "user_id", nullable = false, unique = true, updatable = false)
	private User user;

	@Column(name = "account_number", nullable = false, unique = true, length = 20)
	private String accountNumber;

	@Column(name = "balance", nullable = false, precision = 19, scale = 2)
	private BigDecimal balance = new BigDecimal("0.00");

	@JdbcTypeCode(SqlTypes.CHAR)
	@Column(name = "currency", nullable = false, length = 3)
	private String currency = "BRL";

	@Enumerated(EnumType.STRING)
	@Column(name = "status", nullable = false, length = 20)
	private AccountStatus status = AccountStatus.ACTIVE;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	protected Account() {
	}

	public Account(User user, String accountNumber) {
		this.user = Objects.requireNonNull(user, "user");
		this.accountNumber = Objects.requireNonNull(accountNumber, "accountNumber");
		this.user.linkAccount(this);
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

	public void debit(BigDecimal amount) {
		requirePositive(amount);
		if (balance.compareTo(amount) < 0) {
			throw new BusinessException("INSUFFICIENT_BALANCE", "Insufficient balance");
		}
		balance = balance.subtract(amount);
	}

	public void credit(BigDecimal amount) {
		requirePositive(amount);
		balance = balance.add(amount);
	}

	private static void requirePositive(BigDecimal amount) {
		if (amount == null || amount.signum() <= 0) {
			throw new IllegalArgumentException("amount must be positive");
		}
	}

	public UUID getId() {
		return id;
	}

	public User getUser() {
		return user;
	}

	public String getAccountNumber() {
		return accountNumber;
	}

	public BigDecimal getBalance() {
		return balance;
	}

	public String getCurrency() {
		return currency;
	}

	public AccountStatus getStatus() {
		return status;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}

}
