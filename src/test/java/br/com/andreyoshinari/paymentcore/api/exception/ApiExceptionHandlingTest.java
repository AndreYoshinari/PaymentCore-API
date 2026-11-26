package br.com.andreyoshinari.paymentcore.api.exception;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ApiExceptionHandlingTest {

	@Autowired
	private MockMvc mockMvc;

	@Test
	void authenticationFailureUsesUnauthorizedBody() throws Exception {
		mockMvc.perform(get("/test-support/errors/not-found"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.status").value(401))
				.andExpect(jsonPath("$.code").value("AUTHENTICATION_FAILED"))
				.andExpect(jsonPath("$.message").value("Authentication failed"))
				.andExpect(jsonPath("$.path").value("/test-support/errors/not-found"))
				.andExpect(jsonPath("$.timestamp").exists())
				.andExpect(jsonPath("$.errors").doesNotExist());
	}

	@Test
	@WithMockUser
	void validationFailureListsFieldsWithoutPassword() throws Exception {
		String body = """
				{"name":" ","email":"not-an-email","password":"short"}
				""";

		mockMvc.perform(post("/test-support/errors/validation")
						.with(csrf())
						.contentType(MediaType.APPLICATION_JSON)
						.content(body))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
				.andExpect(jsonPath("$.errors[*].field", containsInAnyOrder("name", "email", "password")))
				.andExpect(content().string(not(org.hamcrest.Matchers.containsString("short"))));
	}

	@Test
	@WithMockUser
	void malformedBodyDoesNotExposeParserDetails() throws Exception {
		mockMvc.perform(post("/test-support/errors/validation")
						.with(csrf())
						.contentType(MediaType.APPLICATION_JSON)
						.content("{"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"))
				.andExpect(jsonPath("$.message").value("Malformed request body"));
	}

	@Test
	@WithMockUser
	void resourceNotFoundUsesNotFound() throws Exception {
		mockMvc.perform(get("/test-support/errors/not-found"))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"))
				.andExpect(jsonPath("$.message").value("User not found"));
	}

	@Test
	@WithMockUser
	void duplicatedEmailUsesConflict() throws Exception {
		mockMvc.perform(get("/test-support/errors/email"))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("EMAIL_ALREADY_REGISTERED"))
				.andExpect(jsonPath("$.message").value("Email is already registered"));
	}

	@Test
	@WithMockUser
	void businessRuleUsesUnprocessableEntity() throws Exception {
		mockMvc.perform(get("/test-support/errors/business"))
				.andExpect(status().isUnprocessableContent())
				.andExpect(jsonPath("$.code").value("BUSINESS_RULE_VIOLATION"))
				.andExpect(jsonPath("$.message").value("Insufficient balance"));
	}

	@Test
	@WithMockUser
	void accessDeniedHidesInternalDetail() throws Exception {
		mockMvc.perform(get("/test-support/errors/denied"))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("ACCESS_DENIED"))
				.andExpect(jsonPath("$.message").value("Access denied"))
				.andExpect(content().string(not(org.hamcrest.Matchers.containsString("hidden detail"))));
	}

	@Test
	@WithMockUser
	void unexpectedErrorHidesCause() throws Exception {
		mockMvc.perform(get("/test-support/errors/unexpected"))
				.andExpect(status().isInternalServerError())
				.andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
				.andExpect(jsonPath("$.message").value("An unexpected error occurred"))
				.andExpect(content().string(not(org.hamcrest.Matchers.containsString("secret"))))
				.andExpect(content().string(not(org.hamcrest.Matchers.containsString("IllegalStateException"))));
	}

	@Test
	@WithMockUser
	void unsupportedMethodKeepsItsStatus() throws Exception {
		mockMvc.perform(get("/test-support/errors/validation"))
				.andExpect(status().isMethodNotAllowed())
				.andExpect(jsonPath("$.status").value(405));
	}

}
