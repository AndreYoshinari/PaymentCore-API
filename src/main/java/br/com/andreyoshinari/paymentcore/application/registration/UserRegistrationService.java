package br.com.andreyoshinari.paymentcore.application.registration;

import br.com.andreyoshinari.paymentcore.api.dto.request.UserRegistrationRequest;
import br.com.andreyoshinari.paymentcore.api.dto.response.RegistrationResponse;
import br.com.andreyoshinari.paymentcore.application.EmailNormalizer;
import br.com.andreyoshinari.paymentcore.domain.entity.Account;
import br.com.andreyoshinari.paymentcore.domain.entity.User;
import br.com.andreyoshinari.paymentcore.domain.exception.EmailAlreadyRegisteredException;
import br.com.andreyoshinari.paymentcore.domain.repository.AccountRepository;
import br.com.andreyoshinari.paymentcore.domain.repository.UserRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserRegistrationService {

	private static final int MAX_ACCOUNT_NUMBER_ATTEMPTS = 5;

	private final UserRepository userRepository;
	private final AccountRepository accountRepository;
	private final PasswordEncoder passwordEncoder;
	private final AccountNumberGenerator accountNumberGenerator;

	public UserRegistrationService(
			UserRepository userRepository,
			AccountRepository accountRepository,
			PasswordEncoder passwordEncoder,
			AccountNumberGenerator accountNumberGenerator) {
		this.userRepository = userRepository;
		this.accountRepository = accountRepository;
		this.passwordEncoder = passwordEncoder;
		this.accountNumberGenerator = accountNumberGenerator;
	}

	@Transactional
	public RegistrationResponse register(UserRegistrationRequest request) {
		String email = EmailNormalizer.normalize(request.email());
		if (userRepository.existsByEmail(email)) {
			throw new EmailAlreadyRegisteredException();
		}

		User user = new User(request.name().trim(), email, passwordEncoder.encode(request.password()));
		try {
			User savedUser = userRepository.saveAndFlush(user);
			Account savedAccount = accountRepository.saveAndFlush(new Account(savedUser, nextAccountNumber()));
			return RegistrationMapper.toResponse(savedUser, savedAccount);
		} catch (DataIntegrityViolationException exception) {
			if (isEmailAlreadyRegistered(exception)) {
				throw new EmailAlreadyRegisteredException();
			}
			throw exception;
		}
	}

	private String nextAccountNumber() {
		for (int attempt = 0; attempt < MAX_ACCOUNT_NUMBER_ATTEMPTS; attempt++) {
			String accountNumber = accountNumberGenerator.generate();
			if (!accountRepository.existsByAccountNumber(accountNumber)) {
				return accountNumber;
			}
		}
		throw new IllegalStateException("Could not generate a unique account number");
	}

	private boolean isEmailAlreadyRegistered(Throwable exception) {
		Throwable current = exception;
		while (current != null) {
			String message = current.getMessage();
			if (message != null && message.contains("uq_users_email")) {
				return true;
			}
			current = current.getCause();
		}
		return false;
	}

}
