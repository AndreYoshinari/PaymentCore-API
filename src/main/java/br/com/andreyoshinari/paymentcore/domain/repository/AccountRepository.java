package br.com.andreyoshinari.paymentcore.domain.repository;

import br.com.andreyoshinari.paymentcore.domain.entity.Account;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface AccountRepository extends JpaRepository<Account, UUID> {

	Optional<Account> findByUserId(UUID userId);

	boolean existsByAccountNumber(String accountNumber);

	@Query("select a.id from Account a where a.user.id = :userId")
	Optional<UUID> findIdByUserId(@Param("userId") UUID userId);

	@Query("select a.id from Account a where a.accountNumber = :accountNumber")
	Optional<UUID> findIdByAccountNumber(@Param("accountNumber") String accountNumber);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select a from Account a where a.id = :id")
	Optional<Account> findByIdForUpdate(@Param("id") UUID id);

}
