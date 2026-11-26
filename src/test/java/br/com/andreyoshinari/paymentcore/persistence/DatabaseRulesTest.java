package br.com.andreyoshinari.paymentcore.persistence;

import br.com.andreyoshinari.paymentcore.support.PostgresTestcontainersInitializer;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class DatabaseRulesTest {

	private static final JsonMapper JSON = new JsonMapper();

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private EntityManager entityManager;

	@Autowired
	private PlatformTransactionManager transactionManager;

	@Autowired
	private DataSource dataSource;

	private UUID senderId;
	private UUID recipientId;

	@BeforeEach
	void createAccounts() throws Exception {
		senderId = accountId(register(email()));
		recipientId = accountId(register(email()));
	}

	@Test
	void appliesTheMigrationsOnTheTestcontainersDatabase() throws SQLException {
		try (var connection = dataSource.getConnection()) {
			assertThat(connection.getMetaData().getURL())
					.contains(String.valueOf(PostgresTestcontainersInitializer.postgres().getMappedPort(5432)));
		}

		assertThat(query("select version from flyway_schema_history where success order by installed_rank"))
				.containsExactly("1", "2", "3");
	}

	@Test
	void rejectsANonPositiveTransferAmount() {
		assertThatThrownBy(() -> inNewTransaction(() ->
				insertTransfer(senderId, recipientId, new BigDecimal("0.00"), "amount-zero")))
				.isInstanceOf(PersistenceException.class)
				.satisfies(error -> assertConstraint(error, "ck_transfers_amount_positive"));

		assertThat(countTransfers("amount-zero")).isZero();
	}

	@Test
	void rejectsATransferBetweenTheSameAccount() {
		assertThatThrownBy(() -> inNewTransaction(() ->
				insertTransfer(senderId, senderId, new BigDecimal("10.00"), "same-account")))
				.isInstanceOf(PersistenceException.class)
				.satisfies(error -> assertConstraint(error, "ck_transfers_distinct_accounts"));
	}

	@Test
	void rejectsANegativeBalance() {
		assertThatThrownBy(() -> inNewTransaction(() -> entityManager
				.createNativeQuery("update accounts set balance = -1.00 where id = :id")
				.setParameter("id", senderId)
				.executeUpdate()))
				.isInstanceOf(PersistenceException.class)
				.satisfies(error -> assertConstraint(error, "ck_accounts_balance_non_negative"));

		assertThat(balance(senderId)).isEqualByComparingTo("0.00");
	}

	@Test
	void rejectsARepeatedIdempotencyKey() {
		inNewTransaction(() -> insertTransfer(senderId, recipientId, new BigDecimal("10.00"), "same-key"));

		assertThatThrownBy(() -> inNewTransaction(() ->
				insertTransfer(senderId, recipientId, new BigDecimal("15.00"), "same-key")))
				.isInstanceOf(PersistenceException.class)
				.satisfies(error -> assertConstraint(error, "uq_transfers_idempotency_key"));

		assertThat(countTransfers("same-key")).isEqualTo(1);
	}

	@Test
	void rollsBackTheBalanceWhenTheTransferCannotBeStored() {
		assertThatThrownBy(() -> inNewTransaction(() -> {
			entityManager.createNativeQuery("update accounts set balance = 50.00 where id = :id")
					.setParameter("id", senderId)
					.executeUpdate();
			entityManager.flush();
			insertTransfer(senderId, recipientId, new BigDecimal("-1.00"), "rolled-back");
		})).isInstanceOf(PersistenceException.class);

		assertThat(balance(senderId)).isEqualByComparingTo("0.00");
		assertThat(countTransfers("rolled-back")).isZero();
	}

	private void insertTransfer(UUID sender, UUID recipient, BigDecimal amount, String idempotencyKey) {
		entityManager.createNativeQuery("""
						insert into transfers (
							id, sender_account_id, recipient_account_id, amount, status, idempotency_key, created_at, updated_at
						) values (
							:id, :sender, :recipient, :amount, 'COMPLETED', :idempotencyKey, now(), now()
						)
						""")
				.setParameter("id", UUID.randomUUID())
				.setParameter("sender", sender)
				.setParameter("recipient", recipient)
				.setParameter("amount", amount)
				.setParameter("idempotencyKey", idempotencyKey)
				.executeUpdate();
	}

	private void inNewTransaction(Runnable work) {
		TransactionTemplate template = new TransactionTemplate(transactionManager);
		template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
		template.executeWithoutResult(status -> work.run());
	}

	private BigDecimal balance(UUID accountId) {
		return new TransactionTemplate(transactionManager).execute(status ->
				(BigDecimal) entityManager.createNativeQuery("select balance from accounts where id = :id")
						.setParameter("id", accountId)
						.getSingleResult());
	}

	private long countTransfers(String idempotencyKey) {
		Number count = new TransactionTemplate(transactionManager).execute(status ->
				(Number) entityManager.createNativeQuery(
								"select count(*) from transfers where idempotency_key = :idempotencyKey")
						.setParameter("idempotencyKey", idempotencyKey)
						.getSingleResult());
		return count.longValue();
	}

	@SuppressWarnings("unchecked")
	private List<String> query(String sql) {
		return new TransactionTemplate(transactionManager).execute(status ->
				entityManager.createNativeQuery(sql).getResultList());
	}

	private void assertConstraint(Throwable error, String constraint) {
		StringBuilder messages = new StringBuilder();
		Throwable current = error;
		while (current != null) {
			if (current.getMessage() != null) {
				messages.append(current.getMessage());
			}
			current = current.getCause();
		}
		assertThat(messages.toString()).contains(constraint);
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

	private UUID accountId(JsonNode registeredUser) {
		return UUID.fromString(registeredUser.get("account").get("id").asString());
	}

	private String email() {
		return "ana-" + UUID.randomUUID() + "@example.com";
	}

}
