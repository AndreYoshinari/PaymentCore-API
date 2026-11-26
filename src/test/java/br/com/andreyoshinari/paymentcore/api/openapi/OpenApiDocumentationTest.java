package br.com.andreyoshinari.paymentcore.api.openapi;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class OpenApiDocumentationTest {

	@Autowired
	private MockMvc mockMvc;

	@Test
	void publishesTheApiAndSwaggerUiWithoutAToken() throws Exception {
		String spec = mockMvc.perform(get("/v3/api-docs"))
				.andExpect(status().isOk())
				.andReturn()
				.getResponse()
				.getContentAsString();

		assertThat(spec).contains(
				"/api/v1/auth/register",
				"/api/v1/auth/login",
				"/api/v1/accounts/me",
				"/api/v1/accounts/me/balance",
				"/api/v1/accounts/me/transactions",
				"/api/v1/transfers",
				"/api/v1/transfers/{transferId}/reverse",
				"Authentication",
				"Accounts",
				"Transfers",
				"Idempotency-Key",
				"bearer");
		assertThat(spec).doesNotContain("test-only-secret-key-with-32-bytes-min!!");
		assertThat(spec).doesNotContain("payment_password");

		mockMvc.perform(get("/swagger-ui/index.html"))
				.andExpect(status().isOk());
	}

}
