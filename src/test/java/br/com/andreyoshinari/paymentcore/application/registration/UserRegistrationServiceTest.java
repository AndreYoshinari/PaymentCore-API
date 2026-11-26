package br.com.andreyoshinari.paymentcore.application.registration;

import br.com.andreyoshinari.paymentcore.api.dto.request.UserRegistrationRequest;
import br.com.andreyoshinari.paymentcore.api.dto.response.RegistrationResponse;
import br.com.andreyoshinari.paymentcore.domain.entity.Account;
import br.com.andreyoshinari.paymentcore.domain.entity.User;
import br.com.andreyoshinari.paymentcore.domain.enums.AccountStatus;
import br.com.andreyoshinari.paymentcore.domain.exception.EmailAlreadyRegisteredException;
import br.com.andreyoshinari.paymentcore.domain.repository.AccountRepository;
import br.com.andreyoshinari.paymentcore.domain.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserRegistrationServiceTest {

	@Mock
	private UserRepository userRepository;

	@Mock
	private AccountRepository accountRepository;

	@Mock
	private PasswordEncoder passwordEncoder;

	private UserRegistrationService service;

	@BeforeEach
	void setUp() {
		service = new UserRegistrationService(
				userRepository,
				accountRepository,
				passwordEncoder,
				new AccountNumberGenerator());
	}

	@Test
	void registersUserWithHashedPasswordAndActiveAccount() {
		when(passwordEncoder.encode("secret123")).thenReturn("$2a$10$hashed-password");
		when(userRepository.saveAndFlush(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
		when(accountRepository.saveAndFlush(any(Account.class))).thenAnswer(invocation -> invocation.getArgument(0));

		RegistrationResponse response = service.register(request("  Ana Silva  ", "Ana@Example.com", "secret123"));

		ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
		verify(userRepository).saveAndFlush(userCaptor.capture());
		User savedUser = userCaptor.getValue();
		assertThat(savedUser.getName()).isEqualTo("Ana Silva");
		assertThat(savedUser.getEmail()).isEqualTo("ana@example.com");
		assertThat(savedUser.getPasswordHash()).isEqualTo("$2a$10$hashed-password");
		assertThat(savedUser.getPasswordHash()).isNotEqualTo("secret123");
		assertThat(savedUser.isActive()).isTrue();

		ArgumentCaptor<Account> accountCaptor = ArgumentCaptor.forClass(Account.class);
		verify(accountRepository).saveAndFlush(accountCaptor.capture());
		Account savedAccount = accountCaptor.getValue();
		assertThat(savedAccount.getUser()).isSameAs(savedUser);
		assertThat(savedAccount.getAccountNumber()).matches("[1-9][0-9]{11}");
		assertThat(savedAccount.getBalance()).isEqualByComparingTo(new BigDecimal("0.00"));
		assertThat(savedAccount.getCurrency()).isEqualTo("BRL");
		assertThat(savedAccount.getStatus()).isEqualTo(AccountStatus.ACTIVE);

		assertThat(response.user().name()).isEqualTo("Ana Silva");
		assertThat(response.user().email()).isEqualTo("ana@example.com");
		assertThat(response.account().accountNumber()).isEqualTo(savedAccount.getAccountNumber());
		assertThat(response.account().balance()).isEqualByComparingTo(new BigDecimal("0.00"));
		assertThat(response.account().currency()).isEqualTo("BRL");
		assertThat(response.account().status()).isEqualTo(AccountStatus.ACTIVE);
	}

	@Test
	void rejectsEmailThatIsAlreadyRegistered() {
		when(userRepository.existsByEmail("ana@example.com")).thenReturn(true);

		assertThatThrownBy(() -> service.register(request("Ana Silva", "ana@example.com", "secret123")))
				.isInstanceOf(EmailAlreadyRegisteredException.class);

		verify(userRepository, never()).saveAndFlush(any());
		verify(accountRepository, never()).saveAndFlush(any());
	}

	@Test
	void translatesConcurrentEmailConflictAndDoesNotCreateAccount() {
		when(passwordEncoder.encode("secret123")).thenReturn("$2a$10$hashed-password");
		when(userRepository.saveAndFlush(any(User.class))).thenThrow(
				new DataIntegrityViolationException("duplicate key value violates unique constraint \"uq_users_email\""));

		assertThatThrownBy(() -> service.register(request("Ana Silva", "ana@example.com", "secret123")))
				.isInstanceOf(EmailAlreadyRegisteredException.class);

		verify(accountRepository, never()).saveAndFlush(any());
	}

	@Test
	void propagatesAccountCreationFailure() {
		when(passwordEncoder.encode("secret123")).thenReturn("$2a$10$hashed-password");
		when(userRepository.saveAndFlush(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
		when(accountRepository.saveAndFlush(any(Account.class))).thenThrow(new IllegalStateException("account failed"));

		assertThatThrownBy(() -> service.register(request("Ana Silva", "ana@example.com", "secret123")))
				.isInstanceOf(IllegalStateException.class)
				.hasMessage("account failed");
	}

	private UserRegistrationRequest request(String name, String email, String password) {
		return new UserRegistrationRequest(name, email, password);
	}

}
