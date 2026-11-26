package br.com.andreyoshinari.paymentcore.application.transfer;

import br.com.andreyoshinari.paymentcore.api.dto.request.TransferRequest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

@Service
public class TransferService {

	private final TransferExecutor transferExecutor;

	public TransferService(TransferExecutor transferExecutor) {
		this.transferExecutor = transferExecutor;
	}

	public TransferOutcome transfer(String idempotencyKey, TransferRequest request) {
		try {
			return transferExecutor.execute(idempotencyKey, request);
		} catch (DataIntegrityViolationException exception) {
			if (!isIdempotencyConstraint(exception)) {
				throw exception;
			}
			return transferExecutor.replay(idempotencyKey, request);
		}
	}

	private boolean isIdempotencyConstraint(Throwable exception) {
		Throwable current = exception;
		while (current != null) {
			String message = current.getMessage();
			if (message != null && message.contains("uq_transfers_idempotency_key")) {
				return true;
			}
			current = current.getCause();
		}
		return false;
	}

}
