package br.com.andreyoshinari.paymentcore.application.authentication;

import br.com.andreyoshinari.paymentcore.api.dto.request.LoginRequest;
import br.com.andreyoshinari.paymentcore.api.dto.response.LoginResponse;
import br.com.andreyoshinari.paymentcore.application.EmailNormalizer;
import br.com.andreyoshinari.paymentcore.domain.entity.User;
import br.com.andreyoshinari.paymentcore.domain.repository.UserRepository;
import br.com.andreyoshinari.paymentcore.security.jwt.IssuedToken;
import br.com.andreyoshinari.paymentcore.security.jwt.JwtTokenProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class LoginService {

	private final UserRepository userRepository;
	private final PasswordEncoder passwordEncoder;
	private final JwtTokenProvider jwtTokenProvider;
	private final String dummyPasswordHash;

	public LoginService(
			UserRepository userRepository,
			PasswordEncoder passwordEncoder,
			JwtTokenProvider jwtTokenProvider) {
		this.userRepository = userRepository;
		this.passwordEncoder = passwordEncoder;
		this.jwtTokenProvider = jwtTokenProvider;
		this.dummyPasswordHash = passwordEncoder.encode("dummy-password-not-used");
	}

	public LoginResponse login(LoginRequest request) {
		User user = userRepository.findByEmail(EmailNormalizer.normalize(request.email())).orElse(null);
		if (user == null) {
			passwordEncoder.matches(request.password(), dummyPasswordHash);
			throw new BadCredentialsException("Authentication failed");
		}
		boolean passwordMatches = passwordEncoder.matches(request.password(), user.getPasswordHash());
		if (!user.isActive() || !passwordMatches) {
			throw new BadCredentialsException("Authentication failed");
		}
		IssuedToken token = jwtTokenProvider.issue(user.getId());
		return new LoginResponse(token.value(), JwtTokenProvider.TOKEN_TYPE, token.expiresIn().toSeconds());
	}

}
