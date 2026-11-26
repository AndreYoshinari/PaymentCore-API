package br.com.andreyoshinari.paymentcore.api.controller;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class TransferControllerTest {

	private static final JsonMapper JSON = new JsonMapper();

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private EntityManager entityManager;

	@Test
	void transfersMoneyAndRecordsBothMovements() throws Exception {
		JsonNode sender = register(email());
		JsonNode recipient = register(email());
		UUID senderId = accountId(sender);
		UUID recipientId = accountId(recipient);
		fund(senderId, "100.00");

		MvcResult result = transfer(token(sender), "key-1", accountNumber(recipient), "40.00", "Lunch")
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.senderAccountNumber").value(accountNumber(sender)))
				.andExpect(jsonPath("$.recipientAccountNumber").value(accountNumber(recipient)))
				.andExpect(jsonPath("$.amount").value(40.0))
				.andExpect(jsonPath("$.description").value("Lunch"))
				.andExpect(jsonPath("$.status").value("COMPLETED"))
				.andExpect(jsonPath("$.password").doesNotExist())
				.andReturn();

		UUID transferId = UUID.fromString(JSON.readTree(result.getResponse().getContentAsString()).get("id").asString());
		assertThat(balance(senderId)).isEqualByComparingTo("60.00");
		assertThat(balance(recipientId)).isEqualByComparingTo("40.00");
		assertThat(movementTypes(transferId)).containsExactlyInAnyOrder("TRANSFER_SENT", "TRANSFER_RECEIVED");
	}

	@Test
	void repeatedIdempotencyKeyReturnsTheOriginalTransfer() throws Exception {
		JsonNode sender = register(email());
		JsonNode recipient = register(email());
		UUID senderId = accountId(sender);
		fund(senderId, "100.00");
		String token = token(sender);

		MvcResult first = transfer(token, "key-1", accountNumber(recipient), "25.00", "Rent")
				.andExpect(status().isCreated())
				.andReturn();
		String transferId = JSON.readTree(first.getResponse().getContentAsString()).get("id").asString();

		transfer(token, "key-1", accountNumber(recipient), "25.00", "Rent")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(transferId));

		assertThat(balance(senderId)).isEqualByComparingTo("75.00");
	}

	@Test
	void repeatedIdempotencyKeyWithDifferentDataConflicts() throws Exception {
		JsonNode sender = register(email());
		JsonNode recipient = register(email());
		UUID senderId = accountId(sender);
		fund(senderId, "100.00");
		String token = token(sender);
		transfer(token, "key-1", accountNumber(recipient), "25.00", "Rent")
				.andExpect(status().isCreated());

		transfer(token, "key-1", accountNumber(recipient), "10.00", "Rent")
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("IDEMPOTENCY_CONFLICT"));

		assertThat(balance(senderId)).isEqualByComparingTo("75.00");
	}

	@Test
	void rejectsInsufficientBalanceWithoutChangingBalances() throws Exception {
		JsonNode sender = register(email());
		JsonNode recipient = register(email());
		UUID senderId = accountId(sender);
		UUID recipientId = accountId(recipient);
		fund(senderId, "10.00");

		transfer(token(sender), "key-1", accountNumber(recipient), "25.00", null)
				.andExpect(status().isUnprocessableContent())
				.andExpect(jsonPath("$.code").value("INSUFFICIENT_BALANCE"));

		assertThat(balance(senderId)).isEqualByComparingTo("10.00");
		assertThat(balance(recipientId)).isEqualByComparingTo("0.00");
	}

	@Test
	void rejectsTransferToTheSameAccount() throws Exception {
		JsonNode sender = register(email());
		UUID senderId = accountId(sender);
		fund(senderId, "100.00");

		transfer(token(sender), "key-1", accountNumber(sender), "10.00", null)
				.andExpect(status().isUnprocessableContent())
				.andExpect(jsonPath("$.code").value("SAME_ACCOUNT"));

		assertThat(balance(senderId)).isEqualByComparingTo("100.00");
	}

	@Test
	void rejectsBlockedRecipient() throws Exception {
		JsonNode sender = register(email());
		JsonNode recipient = register(email());
		UUID senderId = accountId(sender);
		UUID recipientId = accountId(recipient);
		fund(senderId, "100.00");
		entityManager.createNativeQuery("update accounts set status = 'BLOCKED' where id = :id")
				.setParameter("id", recipientId)
				.executeUpdate();
		entityManager.clear();

		transfer(token(sender), "key-1", accountNumber(recipient), "10.00", null)
				.andExpect(status().isUnprocessableContent())
				.andExpect(jsonPath("$.code").value("ACCOUNT_NOT_ACTIVE"));

		assertThat(balance(senderId)).isEqualByComparingTo("100.00");
		assertThat(balance(recipientId)).isEqualByComparingTo("0.00");
	}

	@Test
	void rejectsInvalidAmount() throws Exception {
		JsonNode sender = register(email());
		JsonNode recipient = register(email());
		fund(accountId(sender), "100.00");

		mockMvc.perform(post("/api/v1/transfers")
						.header("Idempotency-Key", "key-1")
						.header("Authorization", "Bearer " + token(sender))
						.contentType(MediaType.APPLICATION_JSON)
						.content(body(accountNumber(recipient), "0.00", null)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
	}

	@Test
	void requiresAuthenticationAndIdempotencyKey() throws Exception {
		mockMvc.perform(post("/api/v1/transfers")
						.contentType(MediaType.APPLICATION_JSON)
						.content(body("123456789012", "10.00", null)))
				.andExpect(status().isUnauthorized());

		JsonNode sender = register(email());
		mockMvc.perform(post("/api/v1/transfers")
						.header("Authorization", "Bearer " + token(sender))
						.contentType(MediaType.APPLICATION_JSON)
						.content(body("123456789012", "10.00", null)))
				.andExpect(status().isBadRequest());
	}

	private org.springframework.test.web.servlet.ResultActions transfer(
			String token,
			String idempotencyKey,
			String recipientAccountNumber,
			String amount,
			String description) throws Exception {
		return mockMvc.perform(post("/api/v1/transfers")
				.header("Authorization", "Bearer " + token)
				.header("Idempotency-Key", idempotencyKey)
				.contentType(MediaType.APPLICATION_JSON)
				.content(body(recipientAccountNumber, amount, description)));
	}

	private String body(String recipientAccountNumber, String amount, String description) {
		String descriptionJson = description == null ? "null" : "\"" + description + "\"";
		return """
				{"recipientAccountNumber":"%s","amount":%s,"description":%s}
				""".formatted(recipientAccountNumber, amount, descriptionJson);
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

	private String token(JsonNode registeredUser) throws Exception {
		MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"email":"%s","password":"secret123"}
								""".formatted(registeredUser.get("user").get("email").asString())))
				.andExpect(status().isOk())
				.andReturn();
		return JSON.readTree(result.getResponse().getContentAsString()).get("accessToken").asString();
	}

	private void fund(UUID accountId, String amount) {
		entityManager.clear();
		entityManager.createNativeQuery("update accounts set balance = :amount where id = :id")
				.setParameter("amount", new BigDecimal(amount))
				.setParameter("id", accountId)
				.executeUpdate();
		entityManager.clear();
	}

	private BigDecimal balance(UUID accountId) {
		return (BigDecimal) entityManager.createNativeQuery("select balance from accounts where id = :id")
				.setParameter("id", accountId)
				.getSingleResult();
	}

	@SuppressWarnings("unchecked")
	private List<String> movementTypes(UUID transferId) {
		return entityManager.createNativeQuery(
						"select transaction_type from account_transactions where transfer_id = :id")
				.setParameter("id", transferId)
				.getResultList();
	}

	private UUID accountId(JsonNode registeredUser) {
		return UUID.fromString(registeredUser.get("account").get("id").asString());
	}

	private String accountNumber(JsonNode registeredUser) {
		return registeredUser.get("account").get("accountNumber").asString();
	}

	private String email() {
		return "ana-" + UUID.randomUUID() + "@example.com";
	}

}
