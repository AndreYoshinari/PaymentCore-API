package br.com.andreyoshinari.paymentcore.application.registration;

import br.com.andreyoshinari.paymentcore.api.dto.response.AccountResponse;
import br.com.andreyoshinari.paymentcore.api.dto.response.RegistrationResponse;
import br.com.andreyoshinari.paymentcore.api.dto.response.UserResponse;
import br.com.andreyoshinari.paymentcore.domain.entity.Account;
import br.com.andreyoshinari.paymentcore.domain.entity.User;

final class RegistrationMapper {

	private RegistrationMapper() {
	}

	static RegistrationResponse toResponse(User user, Account account) {
		return new RegistrationResponse(
				new UserResponse(user.getId(), user.getName(), user.getEmail(), user.getCreatedAt()),
				new AccountResponse(
						account.getId(),
						account.getAccountNumber(),
						account.getBalance(),
						account.getCurrency(),
						account.getStatus(),
						account.getCreatedAt()));
	}

}
