package br.com.andreyoshinari.paymentcore.domain.repository;

import br.com.andreyoshinari.paymentcore.domain.entity.Transfer;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface TransferRepository extends JpaRepository<Transfer, UUID> {

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select transfer from Transfer transfer where transfer.id = :id")
	Optional<Transfer> findByIdForUpdate(@Param("id") UUID id);

	@Query("""
			select transfer from Transfer transfer
			join fetch transfer.senderAccount
			join fetch transfer.recipientAccount
			where transfer.idempotencyKey = :idempotencyKey
			""")
	Optional<Transfer> findByIdempotencyKey(@Param("idempotencyKey") String idempotencyKey);

}
