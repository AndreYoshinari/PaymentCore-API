package br.com.andreyoshinari.paymentcore.domain.repository;

import br.com.andreyoshinari.paymentcore.domain.entity.AccountTransaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.UUID;

public interface AccountTransactionRepository extends JpaRepository<AccountTransaction, UUID> {

	Page<AccountTransaction> findByAccount_Id(UUID accountId, Pageable pageable);

	Page<AccountTransaction> findByAccount_IdAndCreatedAtGreaterThanEqual(UUID accountId, Instant from, Pageable pageable);

	Page<AccountTransaction> findByAccount_IdAndCreatedAtLessThanEqual(UUID accountId, Instant to, Pageable pageable);

	Page<AccountTransaction> findByAccount_IdAndCreatedAtGreaterThanEqualAndCreatedAtLessThanEqual(
			UUID accountId,
			Instant from,
			Instant to,
			Pageable pageable);

}
