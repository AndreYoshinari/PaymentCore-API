package br.com.andreyoshinari.paymentcore.api.controller;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AuthenticationTest {

	private static final JsonMapper JSON = new JsonMapper();

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JwtEncoder jwtEncoder;

	@Autowired
	private EntityManager entityManager;

	@Test
	void loginReturnsTokenThatAccessesProtectedEndpoints() throws Exception {
		String email = email();
		register(email, "secret123");

		MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
						.contentType(MediaType.APPLICATION_JSON)
						.content(credentials(email, "secret123")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.tokenType").value("Bearer"))
				.andExpect(jsonPath("$.expiresIn").value(900))
				.andExpect(jsonPath("$.accessToken").isNotEmpty())
				.andExpect(jsonPath("$.password").doesNotExist())
				.andExpect(jsonPath("$.passwordHash").doesNotExist())
				.andExpect(cookie().doesNotExist("JSESSIONID"))
				.andExpect(content().string(not(containsString("secret123"))))
				.andReturn();

		String token = JSON.readTree(result.getResponse().getContentAsString()).get("accessToken").asString();
		mockMvc.perform(get("/test-support/errors/not-found")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
				.andExpect(status().isNotFound());
	}

	@Test
	void loginRejectsWrongPassword() throws Exception {
		String email = email();
		register(email, "secret123");

		mockMvc.perform(post("/api/v1/auth/login")
						.contentType(MediaType.APPLICATION_JSON)
						.content(credentials(email, "wrongpass")))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("AUTHENTICATION_FAILED"))
				.andExpect(jsonPath("$.message").value("Authentication failed"))
				.andExpect(content().string(not(containsString("wrongpass"))));
	}

	@Test
	void loginRejectsUnknownUser() throws Exception {
		mockMvc.perform(post("/api/v1/auth/login")
						.contentType(MediaType.APPLICATION_JSON)
						.content(credentials(email(), "secret123")))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("AUTHENTICATION_FAILED"))
				.andExpect(jsonPath("$.message").value("Authentication failed"));
	}

	@Test
	void loginRejectsInactiveUser() throws Exception {
		String email = email();
		register(email, "secret123");
		entityManager.createNativeQuery("update users set active = false where email = :email")
				.setParameter("email", email)
				.executeUpdate();
		entityManager.clear();

		mockMvc.perform(post("/api/v1/auth/login")
						.contentType(MediaType.APPLICATION_JSON)
						.content(credentials(email, "secret123")))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("AUTHENTICATION_FAILED"))
				.andExpect(jsonPath("$.message").value("Authentication failed"));
	}

	@Test
	void invalidTokenIsRejected() throws Exception {
		mockMvc.perform(get("/test-support/errors/not-found")
						.header(HttpHeaders.AUTHORIZATION, "Bearer not-a-valid-token"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("AUTHENTICATION_FAILED"));
	}

	@Test
	void expiredTokenIsRejected() throws Exception {
		Instant now = Instant.now();
		String token = jwtEncoder.encode(JwtEncoderParameters.from(
				JwsHeader.with(MacAlgorithm.HS256).build(),
				JwtClaimsSet.builder()
						.subject(UUID.randomUUID().toString())
						.issuedAt(now.minus(Duration.ofMinutes(10)))
						.expiresAt(now.minus(Duration.ofMinutes(5)))
						.build()))
				.getTokenValue();

		mockMvc.perform(get("/test-support/errors/not-found")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("AUTHENTICATION_FAILED"));
	}

	private void register(String email, String password) throws Exception {
		mockMvc.perform(post("/api/v1/auth/register")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"name":"Ana Silva","email":"%s","password":"%s"}
								""".formatted(email, password)))
				.andExpect(status().isCreated());
	}

	private String credentials(String email, String password) {
		return """
				{"email":"%s","password":"%s"}
				""".formatted(email, password);
	}

	private String email() {
		return "ana-" + UUID.randomUUID() + "@example.com";
	}

}
