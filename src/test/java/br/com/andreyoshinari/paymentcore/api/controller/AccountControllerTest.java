package br.com.andreyoshinari.paymentcore.api.controller;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AccountControllerTest {

	private static final JsonMapper JSON = new JsonMapper();

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private EntityManager entityManager;

	@Test
	void authenticatedUserReadsOwnAccountAndBalance() throws Exception {
		JsonNode registered = register(email());
		String accountNumber = registered.get("account").get("accountNumber").asString();
		String token = login(registered.get("user").get("email").asString());

		mockMvc.perform(get("/api/v1/accounts/me").header(HttpHeaders.AUTHORIZATION, bearer(token)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.accountNumber").value(accountNumber))
				.andExpect(jsonPath("$.balance").value(0))
				.andExpect(jsonPath("$.currency").value("BRL"))
				.andExpect(jsonPath("$.status").value("ACTIVE"))
				.andExpect(jsonPath("$.password").doesNotExist())
				.andExpect(jsonPath("$.passwordHash").doesNotExist());

		mockMvc.perform(get("/api/v1/accounts/me/balance").header(HttpHeaders.AUTHORIZATION, bearer(token)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.accountId").value(registered.get("account").get("id").asString()))
				.andExpect(jsonPath("$.accountNumber").value(accountNumber))
				.andExpect(jsonPath("$.balance").value(0))
				.andExpect(jsonPath("$.currency").value("BRL"))
				.andExpect(jsonPath("$.status").value("ACTIVE"))
				.andExpect(jsonPath("$.passwordHash").doesNotExist());
	}

	@Test
	void accountQueriesRequireAToken() throws Exception {
		mockMvc.perform(get("/api/v1/accounts/me"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("AUTHENTICATION_FAILED"));

		mockMvc.perform(get("/api/v1/accounts/me/balance"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("AUTHENTICATION_FAILED"));
	}

	@Test
	void missingAccountReturnsNotFound() throws Exception {
		JsonNode registered = register(email());
		UUID userId = UUID.fromString(registered.get("user").get("id").asString());
		entityManager.createNativeQuery("delete from accounts where user_id = :userId")
				.setParameter("userId", userId)
				.executeUpdate();
		entityManager.clear();
		String token = login(registered.get("user").get("email").asString());

		mockMvc.perform(get("/api/v1/accounts/me").header(HttpHeaders.AUTHORIZATION, bearer(token)))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));

		mockMvc.perform(get("/api/v1/accounts/me/balance").header(HttpHeaders.AUTHORIZATION, bearer(token)))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
	}

	@Test
	void userCannotReadAnotherUsersAccount() throws Exception {
		JsonNode owner = register(email());
		JsonNode other = register(email());
		String ownerNumber = owner.get("account").get("accountNumber").asString();
		String otherNumber = other.get("account").get("accountNumber").asString();
		String token = login(owner.get("user").get("email").asString());

		mockMvc.perform(get("/api/v1/accounts/me")
						.param("userId", other.get("user").get("id").asString())
						.header(HttpHeaders.AUTHORIZATION, bearer(token)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.accountNumber").value(ownerNumber))
				.andExpect(content().string(not(containsString(otherNumber))));

		mockMvc.perform(get("/api/v1/accounts/" + other.get("account").get("id").asString())
						.header(HttpHeaders.AUTHORIZATION, bearer(token)))
				.andExpect(status().isNotFound())
				.andExpect(content().string(not(containsString(otherNumber))));
	}

	private JsonNode register(String email) throws Exception {
		MvcResult result = mockMvc.perform(post("/api/v1/auth/register")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"name":"Ana Silva","email":"%s","password":"secret123"}
								""".formatted(email)))
				.andExpect(status().isCreated())
				.andReturn();
		return JSON.readTree(result.getResponse().getContentAsString());
	}

	private String login(String email) throws Exception {
		MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"email":"%s","password":"secret123"}
								""".formatted(email)))
				.andExpect(status().isOk())
				.andReturn();
		return JSON.readTree(result.getResponse().getContentAsString()).get("accessToken").asString();
	}

	private String bearer(String token) {
		return "Bearer " + token;
	}

	private String email() {
		return "ana-" + UUID.randomUUID() + "@example.com";
	}

}
