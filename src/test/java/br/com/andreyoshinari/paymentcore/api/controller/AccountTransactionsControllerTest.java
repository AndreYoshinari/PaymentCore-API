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

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
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
class AccountTransactionsControllerTest {

	private static final Instant FIRST = Instant.parse("2026-01-01T10:00:00Z");
	private static final Instant SECOND = Instant.parse("2026-01-02T10:00:00Z");
	private static final Instant THIRD = Instant.parse("2026-01-03T10:00:00Z");

	private static final JsonMapper JSON = new JsonMapper();

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private EntityManager entityManager;

	@Test
	void returnsTheAuthenticatedStatementNewestFirst() throws Exception {
		JsonNode sender = register(email());
		JsonNode recipient = register(email());
		fund(accountId(sender), "100.00");
		String token = login(sender);
		transfer(token, "key-1", accountNumber(recipient), "10.00", "first");
		transfer(token, "key-2", accountNumber(recipient), "20.00", "second");
		transfer(token, "key-3", accountNumber(recipient), "30.00", "third");
		stamp(accountId(sender), "first", FIRST);
		stamp(accountId(sender), "second", SECOND);
		stamp(accountId(sender), "third", THIRD);
		entityManager.clear();

		mockMvc.perform(get("/api/v1/accounts/me/transactions")
						.param("page", "0")
						.param("size", "2")
						.header(HttpHeaders.AUTHORIZATION, bearer(token)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.page").value(0))
				.andExpect(jsonPath("$.size").value(2))
				.andExpect(jsonPath("$.totalElements").value(3))
				.andExpect(jsonPath("$.totalPages").value(2))
				.andExpect(jsonPath("$.content.length()").value(2))
				.andExpect(jsonPath("$.content[0].description").value("third"))
				.andExpect(jsonPath("$.content[0].transactionType").value("TRANSFER_SENT"))
				.andExpect(jsonPath("$.content[0].amount").value(30.0))
				.andExpect(jsonPath("$.content[0].transferId").isNotEmpty())
				.andExpect(jsonPath("$.content[1].description").value("second"))
				.andExpect(jsonPath("$.content[1].amount").value(20.0));

		mockMvc.perform(get("/api/v1/accounts/me/transactions")
						.param("page", "1")
						.param("size", "2")
						.header(HttpHeaders.AUTHORIZATION, bearer(token)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content.length()").value(1))
				.andExpect(jsonPath("$.content[0].description").value("first"))
				.andExpect(jsonPath("$.content[0].amount").value(10.0));

		mockMvc.perform(get("/api/v1/accounts/me/transactions")
						.param("size", "500")
						.header(HttpHeaders.AUTHORIZATION, bearer(token)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.size").value(100))
				.andExpect(jsonPath("$.content.length()").value(3));
	}

	@Test
	void filtersByAnInclusivePeriod() throws Exception {
		JsonNode sender = register(email());
		JsonNode recipient = register(email());
		fund(accountId(sender), "100.00");
		String token = login(sender);
		transfer(token, "key-1", accountNumber(recipient), "10.00", "first");
		transfer(token, "key-2", accountNumber(recipient), "20.00", "second");
		transfer(token, "key-3", accountNumber(recipient), "30.00", "third");
		stamp(accountId(sender), "first", FIRST);
		stamp(accountId(sender), "second", SECOND);
		stamp(accountId(sender), "third", THIRD);
		entityManager.clear();

		mockMvc.perform(get("/api/v1/accounts/me/transactions")
						.param("from", SECOND.toString())
						.param("to", SECOND.toString())
						.header(HttpHeaders.AUTHORIZATION, bearer(token)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalElements").value(1))
				.andExpect(jsonPath("$.content.length()").value(1))
				.andExpect(jsonPath("$.content[0].description").value("second"))
				.andExpect(jsonPath("$.content[0].amount").value(20.0));
	}

	@Test
	void keepsStatementsIsolatedBetweenUsers() throws Exception {
		JsonNode sender = register(email());
		JsonNode recipient = register(email());
		fund(accountId(sender), "100.00");
		String senderToken = login(sender);
		transfer(senderToken, "key-1", accountNumber(recipient), "15.00", "rent");
		entityManager.flush();
		entityManager.clear();

		MvcResult recipientStatement = mockMvc.perform(get("/api/v1/accounts/me/transactions")
						.header(HttpHeaders.AUTHORIZATION, bearer(login(recipient))))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalElements").value(1))
				.andExpect(jsonPath("$.content[0].transactionType").value("TRANSFER_RECEIVED"))
				.andReturn();
		String recipientTransactionId = JSON.readTree(recipientStatement.getResponse().getContentAsString())
				.get("content").get(0).get("id").asString();

		mockMvc.perform(get("/api/v1/accounts/me/transactions")
						.param("accountId", accountId(recipient).toString())
						.header(HttpHeaders.AUTHORIZATION, bearer(senderToken)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalElements").value(1))
				.andExpect(jsonPath("$.content[0].transactionType").value("TRANSFER_SENT"))
				.andExpect(content().string(not(containsString(recipientTransactionId))));
	}

	@Test
	void rejectsAnInvalidPeriodAndAnInvalidDate() throws Exception {
		String token = login(register(email()));

		mockMvc.perform(get("/api/v1/accounts/me/transactions")
						.param("from", "2026-01-03T00:00:00Z")
						.param("to", "2026-01-01T00:00:00Z")
						.header(HttpHeaders.AUTHORIZATION, bearer(token)))
				.andExpect(status().isUnprocessableContent())
				.andExpect(jsonPath("$.code").value("INVALID_PERIOD"));

		mockMvc.perform(get("/api/v1/accounts/me/transactions")
						.param("from", "not-a-date")
						.header(HttpHeaders.AUTHORIZATION, bearer(token)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_PARAMETER"));
	}

	@Test
	void requiresAuthentication() throws Exception {
		mockMvc.perform(get("/api/v1/accounts/me/transactions"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("AUTHENTICATION_FAILED"));
	}

	private void transfer(String token, String key, String recipientAccountNumber, String amount, String description)
			throws Exception {
		mockMvc.perform(post("/api/v1/transfers")
						.header(HttpHeaders.AUTHORIZATION, bearer(token))
						.header("Idempotency-Key", key)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"recipientAccountNumber":"%s","amount":%s,"description":"%s"}
								""".formatted(recipientAccountNumber, amount, description)))
				.andExpect(status().isCreated());
	}

	private void fund(UUID accountId, String amount) {
		entityManager.clear();
		entityManager.createNativeQuery("update accounts set balance = :amount where id = :id")
				.setParameter("amount", new BigDecimal(amount))
				.setParameter("id", accountId)
				.executeUpdate();
		entityManager.clear();
	}

	private void stamp(UUID accountId, String description, Instant createdAt) {
		entityManager.createNativeQuery("""
						update account_transactions
						set created_at = :createdAt
						where account_id = :accountId and description = :description
						""")
				.setParameter("createdAt", Timestamp.from(createdAt))
				.setParameter("accountId", accountId)
				.setParameter("description", description)
				.executeUpdate();
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

	private String login(JsonNode registeredUser) throws Exception {
		MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"email":"%s","password":"secret123"}
								""".formatted(registeredUser.get("user").get("email").asString())))
				.andExpect(status().isOk())
				.andReturn();
		return JSON.readTree(result.getResponse().getContentAsString()).get("accessToken").asString();
	}

	private UUID accountId(JsonNode registeredUser) {
		return UUID.fromString(registeredUser.get("account").get("id").asString());
	}

	private String accountNumber(JsonNode registeredUser) {
		return registeredUser.get("account").get("accountNumber").asString();
	}

	private String bearer(String token) {
		return "Bearer " + token;
	}

	private String email() {
		return "ana-" + UUID.randomUUID() + "@example.com";
	}

}
