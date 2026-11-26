package br.com.andreyoshinari.paymentcore.security;

import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class AuthenticatedUser {

	public UUID id() {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		if (authentication == null || !(authentication.getPrincipal() instanceof Jwt jwt) || jwt.getSubject() == null) {
			throw new BadCredentialsException("Authentication failed");
		}
		try {
			return UUID.fromString(jwt.getSubject());
		} catch (IllegalArgumentException exception) {
			throw new BadCredentialsException("Authentication failed");
		}
	}

}
