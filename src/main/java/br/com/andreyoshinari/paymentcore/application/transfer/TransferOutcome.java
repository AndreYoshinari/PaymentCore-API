package br.com.andreyoshinari.paymentcore.application.transfer;

import br.com.andreyoshinari.paymentcore.api.dto.response.TransferResponse;

public record TransferOutcome(TransferResponse response, boolean created) {
}
