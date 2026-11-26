package br.com.andreyoshinari.paymentcore.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

	@Bean
	PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder();
	}

	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http, ApiSecurityExceptionHandler apiSecurityExceptionHandler)
			throws Exception {
		return http
				.authorizeHttpRequests(authorize -> authorize
						.requestMatchers(HttpMethod.POST, "/api/v1/auth/register", "/api/v1/auth/login").permitAll()
						.requestMatchers("/error", "/actuator/health", "/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs", "/v3/api-docs/**")
						.permitAll()
						.anyRequest().authenticated())
				// Bearer tokens travel in the Authorization header and the API does not create a session.
				// CSRF protects browsers that resend a session cookie, so it does not apply here.
				.csrf(AbstractHttpConfigurer::disable)
				.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				.httpBasic(AbstractHttpConfigurer::disable)
				.formLogin(AbstractHttpConfigurer::disable)
				.oauth2ResourceServer(oauth2 -> oauth2
						.jwt(Customizer.withDefaults())
						.authenticationEntryPoint(apiSecurityExceptionHandler)
						.accessDeniedHandler(apiSecurityExceptionHandler))
				.exceptionHandling(exceptions -> exceptions
						.authenticationEntryPoint(apiSecurityExceptionHandler)
						.accessDeniedHandler(apiSecurityExceptionHandler))
				.build();
	}

}
