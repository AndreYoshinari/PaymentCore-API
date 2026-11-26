package br.com.andreyoshinari.paymentcore.security.jwt;

import java.time.Duration;

public record IssuedToken(String value, Duration expiresIn) {
}
