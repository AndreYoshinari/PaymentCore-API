package br.com.andreyoshinari.paymentcore.support;

import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.util.Map;

public final class PostgresTestcontainersInitializer
		implements ApplicationContextInitializer<ConfigurableApplicationContext> {

	private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine");

	static {
		POSTGRES.start();
	}

	public static PostgreSQLContainer postgres() {
		return POSTGRES;
	}

	@Override
	public void initialize(ConfigurableApplicationContext context) {
		Map<String, Object> properties = Map.of(
				"spring.datasource.url", POSTGRES.getJdbcUrl(),
				"spring.datasource.username", POSTGRES.getUsername(),
				"spring.datasource.password", POSTGRES.getPassword());
		context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("testcontainers", properties));
	}

}
