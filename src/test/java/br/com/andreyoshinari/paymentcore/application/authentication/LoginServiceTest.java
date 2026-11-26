package br.com.andreyoshinari.paymentcore.application.authentication;

import br.com.andreyoshinari.paymentcore.api.dto.request.LoginRequest;
import br.com.andreyoshinari.paymentcore.api.dto.response.LoginResponse;
import br.com.andreyoshinari.paymentcore.domain.entity.User;
import br.com.andreyoshinari.paymentcore.domain.repository.UserRepository;
import br.com.andreyoshinari.paymentcore.security.jwt.IssuedToken;
import br.com.andreyoshinari.paymentcore.security.jwt.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LoginServiceTest {

	@Mock
	private UserRepository userRepository;

	@Mock
	private PasswordEncoder passwordEncoder;

	@Mock
	private JwtTokenProvider jwtTokenProvider;

	private LoginService loginService;

	@BeforeEach
	void setUp() {
		when(passwordEncoder.encode("dummy-password-not-used")).thenReturn("$2a$10$dummy");
		loginService = new LoginService(userRepository, passwordEncoder, jwtTokenProvider);
	}

	@Test
	void loginReturnsBearerTokenWhenCredentialsMatch() {
		User user = new User("Ana Silva", "ana@example.com", "$2a$10$hash");
		when(userRepository.findByEmail("ana@example.com")).thenReturn(Optional.of(user));
		when(passwordEncoder.matches("secret123", "$2a$10$hash")).thenReturn(true);
		when(jwtTokenProvider.issue(any())).thenReturn(new IssuedToken("signed-token", Duration.ofMinutes(15)));

		LoginResponse response = loginService.login(new LoginRequest("Ana@Example.com", "secret123"));

		assertThat(response.accessToken()).isEqualTo("signed-token");
		assertThat(response.tokenType()).isEqualTo("Bearer");
		assertThat(response.expiresIn()).isEqualTo(900);
	}

	@Test
	void loginRejectsWrongPassword() {
		User user = new User("Ana Silva", "ana@example.com", "$2a$10$hash");
		when(userRepository.findByEmail("ana@example.com")).thenReturn(Optional.of(user));
		when(passwordEncoder.matches("wrongpass", "$2a$10$hash")).thenReturn(false);

		assertThatThrownBy(() -> loginService.login(new LoginRequest("ana@example.com", "wrongpass")))
				.isInstanceOf(BadCredentialsException.class);
		verify(jwtTokenProvider, never()).issue(any());
	}

	@Test
	void loginRejectsUnknownUser() {
		when(userRepository.findByEmail("missing@example.com")).thenReturn(Optional.empty());

		assertThatThrownBy(() -> loginService.login(new LoginRequest("missing@example.com", "secret123")))
				.isInstanceOf(BadCredentialsException.class);
		verify(passwordEncoder).matches("secret123", "$2a$10$dummy");
		verify(jwtTokenProvider, never()).issue(any());
	}

	@Test
	void loginRejectsInactiveUser() {
		User user = new User("Ana Silva", "ana@example.com", "$2a$10$hash");
		ReflectionTestUtils.setField(user, "active", false);
		when(userRepository.findByEmail("ana@example.com")).thenReturn(Optional.of(user));
		when(passwordEncoder.matches("secret123", "$2a$10$hash")).thenReturn(true);

		assertThatThrownBy(() -> loginService.login(new LoginRequest("ana@example.com", "secret123")))
				.isInstanceOf(BadCredentialsException.class);
		verify(jwtTokenProvider, never()).issue(any());
	}

}
