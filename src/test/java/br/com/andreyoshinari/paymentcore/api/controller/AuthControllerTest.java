package br.com.andreyoshinari.paymentcore.api.controller;

import br.com.andreyoshinari.paymentcore.domain.entity.Account;
import br.com.andreyoshinari.paymentcore.domain.entity.User;
import br.com.andreyoshinari.paymentcore.domain.enums.AccountStatus;
import br.com.andreyoshinari.paymentcore.domain.repository.AccountRepository;
import br.com.andreyoshinari.paymentcore.domain.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AuthControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private AccountRepository accountRepository;

	@Autowired
	private PasswordEncoder passwordEncoder;

	@Test
	void registerCreatesUserAndAccountWithoutExposingPassword() throws Exception {
		String email = "ana-" + UUID.randomUUID() + "@example.com";

		mockMvc.perform(post("/api/v1/auth/register")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"name":"Ana Silva","email":"%s","password":"secret123"}
								""".formatted(email)))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.user.name").value("Ana Silva"))
				.andExpect(jsonPath("$.user.email").value(email))
				.andExpect(jsonPath("$.user.password").doesNotExist())
				.andExpect(jsonPath("$.user.passwordHash").doesNotExist())
				.andExpect(jsonPath("$.account.accountNumber").exists())
				.andExpect(jsonPath("$.account.balance").value(0))
				.andExpect(jsonPath("$.account.currency").value("BRL"))
				.andExpect(jsonPath("$.account.status").value("ACTIVE"))
				.andExpect(jsonPath("$.password").doesNotExist())
				.andExpect(jsonPath("$.passwordHash").doesNotExist())
				.andExpect(content().string(not(org.hamcrest.Matchers.containsString("secret123"))));

		User user = userRepository.findByEmail(email).orElseThrow();
		assertThat(user.getPasswordHash()).isNotEqualTo("secret123");
		assertThat(passwordEncoder.matches("secret123", user.getPasswordHash())).isTrue();

		Account account = accountRepository.findByUserId(user.getId()).orElseThrow();
		assertThat(account.getBalance()).isEqualByComparingTo(new BigDecimal("0.00"));
		assertThat(account.getCurrency().trim()).isEqualTo("BRL");
		assertThat(account.getStatus()).isEqualTo(AccountStatus.ACTIVE);
		assertThat(account.getAccountNumber()).hasSize(12);
	}

	@Test
	void duplicateEmailReturnsConflictAndKeepsASingleAccount() throws Exception {
		String email = "ana-" + UUID.randomUUID() + "@example.com";
		String body = """
				{"name":"Ana Silva","email":"%s","password":"secret123"}
				""".formatted(email);

		mockMvc.perform(post("/api/v1/auth/register")
						.contentType(MediaType.APPLICATION_JSON)
						.content(body))
				.andExpect(status().isCreated());

		mockMvc.perform(post("/api/v1/auth/register")
						.contentType(MediaType.APPLICATION_JSON)
						.content(body))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("EMAIL_ALREADY_REGISTERED"));

		User user = userRepository.findByEmail(email).orElseThrow();
		assertThat(accountRepository.findByUserId(user.getId())).isPresent();
		assertThat(userRepository.findAll().stream().filter(candidate -> candidate.getEmail().equals(email))).hasSize(1);
	}

	@Test
	void invalidPasswordDoesNotCreateUser() throws Exception {
		String email = "ana-" + UUID.randomUUID() + "@example.com";
		long usersBefore = userRepository.count();

		mockMvc.perform(post("/api/v1/auth/register")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"name":"Ana Silva","email":"%s","password":"short"}
								""".formatted(email)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

		assertThat(userRepository.count()).isEqualTo(usersBefore);
		assertThat(userRepository.findByEmail(email)).isEmpty();
	}

}
