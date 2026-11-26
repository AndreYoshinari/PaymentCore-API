package br.com.andreyoshinari.paymentcore.application.transfer;

import br.com.andreyoshinari.paymentcore.api.dto.request.TransferRequest;
import br.com.andreyoshinari.paymentcore.api.dto.response.TransferResponse;
import br.com.andreyoshinari.paymentcore.domain.enums.TransferStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TransferServiceTest {

	@Mock
	private TransferExecutor transferExecutor;

	@InjectMocks
	private TransferService transferService;

	@Test
	void replaysWhenTheIdempotencyKeyIsCreatedConcurrently() {
		TransferRequest request = new TransferRequest("222222222222", new BigDecimal("10.00"), null);
		TransferOutcome replay = new TransferOutcome(response(), false);
		when(transferExecutor.execute("key-1", request)).thenThrow(new DataIntegrityViolationException(
				"duplicate key value violates unique constraint \"uq_transfers_idempotency_key\""));
		when(transferExecutor.replay("key-1", request)).thenReturn(replay);

		TransferOutcome outcome = transferService.transfer("key-1", request);

		assertThat(outcome).isSameAs(replay);
		verify(transferExecutor).replay("key-1", request);
	}

	@Test
	void doesNotReplayUnrelatedIntegrityFailures() {
		TransferRequest request = new TransferRequest("222222222222", new BigDecimal("10.00"), null);
		when(transferExecutor.execute("key-1", request))
				.thenThrow(new DataIntegrityViolationException("ck_accounts_balance_non_negative"));

		assertThatThrownBy(() -> transferService.transfer("key-1", request))
				.isInstanceOf(DataIntegrityViolationException.class);
		verify(transferExecutor, never()).replay("key-1", request);
	}

	private TransferResponse response() {
		return new TransferResponse(null, "111111111111", "222222222222", new BigDecimal("10.00"), null, TransferStatus.COMPLETED, null, null);
	}

}
