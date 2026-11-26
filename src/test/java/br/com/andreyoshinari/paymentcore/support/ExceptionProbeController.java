package br.com.andreyoshinari.paymentcore.support;

import br.com.andreyoshinari.paymentcore.api.dto.request.UserRegistrationRequest;
import br.com.andreyoshinari.paymentcore.domain.exception.BusinessException;
import br.com.andreyoshinari.paymentcore.domain.exception.EmailAlreadyRegisteredException;
import br.com.andreyoshinari.paymentcore.domain.exception.ResourceNotFoundException;
import jakarta.validation.Valid;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/test-support/errors")
public class ExceptionProbeController {

	@PostMapping("/validation")
	public void validation(@Valid @RequestBody UserRegistrationRequest request) {
	}

	@GetMapping("/not-found")
	public void notFound() {
		throw new ResourceNotFoundException("User not found");
	}

	@GetMapping("/email")
	public void email() {
		throw new EmailAlreadyRegisteredException();
	}

	@GetMapping("/business")
	public void business() {
		throw new BusinessException("Insufficient balance");
	}

	@GetMapping("/denied")
	public void denied() {
		throw new AccessDeniedException("hidden detail");
	}

	@GetMapping("/unexpected")
	public void unexpected() {
		throw new IllegalStateException("database password=secret");
	}

}
