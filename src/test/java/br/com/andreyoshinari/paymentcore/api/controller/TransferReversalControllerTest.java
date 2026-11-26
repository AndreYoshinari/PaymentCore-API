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
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class TransferReversalControllerTest {

	private static final JsonMapper JSON = new JsonMapper();

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private EntityManager entityManager;

	@Test
	void reversesACompletedTransferOnce() throws Exception {
		JsonNode sender = register(email());
		JsonNode recipient = register(email());
		fund(accountId(sender), "100.00");
		String senderToken = login(sender);
		UUID transferId = transfer(senderToken, accountNumber(recipient), "40.00");

		mockMvc.perform(post("/api/v1/transfers/" + transferId + "/reverse")
						.header(HttpHeaders.AUTHORIZATION, bearer(senderToken)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(transferId.toString()))
				.andExpect(jsonPath("$.status").value("REVERSED"))
				.andExpect(jsonPath("$.amount").value(40.0))
				.andExpect(jsonPath("$.reversedAt").isNotEmpty())
				.andExpect(jsonPath("$.senderAccountNumber").value(accountNumber(sender)))
				.andExpect(jsonPath("$.recipientAccountNumber").value(accountNumber(recipient)));

		assertThat(balance(accountId(sender))).isEqualByComparingTo("100.00");
		assertThat(balance(accountId(recipient))).isEqualByComparingTo("0.00");
		assertThat(transferCount(transferId)).isEqualTo(1);
		assertThat(movementTypes(transferId)).containsExactlyInAnyOrder(
				"TRANSFER_SENT",
				"TRANSFER_RECEIVED",
				"REVERSAL_SENT",
				"REVERSAL_RECEIVED");

		mockMvc.perform(post("/api/v1/transfers/" + transferId + "/reverse")
						.header(HttpHeaders.AUTHORIZATION, bearer(senderToken)))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("TRANSFER_NOT_REVERSIBLE"));
		assertThat(balance(accountId(sender))).isEqualByComparingTo("100.00");
		assertThat(balance(accountId(recipient))).isEqualByComparingTo("0.00");
	}

	@Test
	void rejectsReversalWhenTheDestinationHasSpentTheAmount() throws Exception {
		JsonNode sender = register(email());
		JsonNode recipient = register(email());
		fund(accountId(sender), "100.00");
		String senderToken = login(sender);
		UUID transferId = transfer(senderToken, accountNumber(recipient), "40.00");
		entityManager.clear();
		entityManager.createNativeQuery("update accounts set balance = 0.00 where id = :id")
				.setParameter("id", accountId(recipient))
				.executeUpdate();
		entityManager.clear();

		mockMvc.perform(post("/api/v1/transfers/" + transferId + "/reverse")
						.header(HttpHeaders.AUTHORIZATION, bearer(senderToken)))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("INSUFFICIENT_BALANCE"));

		assertThat(balance(accountId(sender))).isEqualByComparingTo("60.00");
		assertThat(balance(accountId(recipient))).isEqualByComparingTo("0.00");
		assertThat(statusOf(transferId)).isEqualTo("COMPLETED");
		assertThat(reversedAt(transferId)).isNull();
	}

	@Test
	void onlyTheSourceAccountOwnerCanReverse() throws Exception {
		JsonNode sender = register(email());
		JsonNode recipient = register(email());
		fund(accountId(sender), "100.00");
		UUID transferId = transfer(login(sender), accountNumber(recipient), "40.00");

		mockMvc.perform(post("/api/v1/transfers/" + transferId + "/reverse")
						.header(HttpHeaders.AUTHORIZATION, bearer(login(recipient))))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

		assertThat(balance(accountId(sender))).isEqualByComparingTo("60.00");
		assertThat(balance(accountId(recipient))).isEqualByComparingTo("40.00");
		assertThat(statusOf(transferId)).isEqualTo("COMPLETED");
	}

	@Test
	void rejectsAnUnknownTransfer() throws Exception {
		String token = login(register(email()));

		mockMvc.perform(post("/api/v1/transfers/" + UUID.randomUUID() + "/reverse")
						.header(HttpHeaders.AUTHORIZATION, bearer(token)))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
	}

	@Test
	void requiresAuthentication() throws Exception {
		mockMvc.perform(post("/api/v1/transfers/" + UUID.randomUUID() + "/reverse"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("AUTHENTICATION_FAILED"));
	}

	private UUID transfer(String token, String recipientAccountNumber, String amount) throws Exception {
		MvcResult result = mockMvc.perform(post("/api/v1/transfers")
						.header(HttpHeaders.AUTHORIZATION, bearer(token))
						.header("Idempotency-Key", "key-" + UUID.randomUUID())
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"recipientAccountNumber":"%s","amount":%s,"description":"Lunch"}
								""".formatted(recipientAccountNumber, amount)))
				.andExpect(status().isCreated())
				.andReturn();
		return UUID.fromString(JSON.readTree(result.getResponse().getContentAsString()).get("id").asString());
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

	private String statusOf(UUID transferId) {
		return (String) entityManager.createNativeQuery("select status from transfers where id = :id")
				.setParameter("id", transferId)
				.getSingleResult();
	}

	private Object reversedAt(UUID transferId) {
		return entityManager.createNativeQuery("select reversed_at from transfers where id = :id")
				.setParameter("id", transferId)
				.getSingleResult();
	}

	private long transferCount(UUID transferId) {
		return ((Number) entityManager.createNativeQuery("select count(*) from transfers where id = :id")
				.setParameter("id", transferId)
				.getSingleResult()).longValue();
	}

	@SuppressWarnings("unchecked")
	private List<String> movementTypes(UUID transferId) {
		return entityManager.createNativeQuery(
						"select transaction_type from account_transactions where transfer_id = :id")
				.setParameter("id", transferId)
				.getResultList();
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
