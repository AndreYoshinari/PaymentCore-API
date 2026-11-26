package br.com.andreyoshinari.paymentcore.api.openapi;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {

	public static final String BEARER_SCHEME = "bearerAuth";

	@Bean
	OpenAPI paymentCoreOpenApi() {
		return new OpenAPI()
				.info(new Info()
						.title("PaymentCore API")
						.version("v1")
						.description("""
								Simulated payment API for study.
								Reversal is a project rule and does not reproduce PIX or any real payment scheme.
								Protected operations expect the access token from login.
								In Authorize, paste only the token. Do not include the Bearer prefix and do not send the signing secret.
								"""))
				.tags(List.of(
						new io.swagger.v3.oas.models.tags.Tag().name("Authentication")
								.description("Registration and login. No token is required."),
						new io.swagger.v3.oas.models.tags.Tag().name("Accounts")
								.description("Account, balance, and statement of the authenticated user."),
						new io.swagger.v3.oas.models.tags.Tag().name("Transfers")
								.description("Simulated transfers and full reversals.")))
				.addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME))
				.components(new Components().addSecuritySchemes(BEARER_SCHEME, new SecurityScheme()
						.type(SecurityScheme.Type.HTTP)
						.scheme("bearer")
						.bearerFormat("JWT")
						.description("Access token returned by login. Paste the token only.")));
	}

	@Bean
	OperationCustomizer hideIgnoredPageSort() {
		return (operation, handlerMethod) -> {
			if (operation.getParameters() != null) {
				operation.getParameters().removeIf(parameter -> "sort".equals(parameter.getName()));
			}
			return operation;
		};
	}

}
