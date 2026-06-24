package com.tomato.modules.auth.service;

import com.tomato.config.JwtProperties;
import com.tomato.modules.user.entity.User;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceTest {

    private static final String SECRET = "test-secret-minimum-32-bytes-long-value";

    @Test
    @DisplayName("Should parse expected claims when token is valid")
    void parse_WithValidToken_ReturnsExpectedClaims() {
        JwtService jwtService = new JwtService(new JwtProperties(SECRET, 3600, "tomato-test"));
        User user = User.builder()
                .id(7)
                .username("tomato")
                .build();

        String token = jwtService.generate(user);
        Claims claims = jwtService.parse(token);

        assertThat(jwtService.isValid(token)).isTrue();
        assertThat(claims.getSubject()).isEqualTo("tomato");
        assertThat(claims.getIssuer()).isEqualTo("tomato-test");
        assertThat(claims.get("uid", Integer.class)).isEqualTo(7);
    }

    @Test
    @DisplayName("Should reject token when it is expired")
    void parse_WithExpiredToken_RejectsToken() {
        JwtService jwtService = new JwtService(new JwtProperties(SECRET, -1, "tomato-test"));
        User user = User.builder()
                .id(7)
                .username("tomato")
                .build();

        String token = jwtService.generate(user);

        assertThat(jwtService.isValid(token)).isFalse();
        assertThatThrownBy(() -> jwtService.parse(token)).isInstanceOf(RuntimeException.class);
    }

    @Test
    @DisplayName("Should reject token when signature is tampered")
    void parse_WithTamperedToken_RejectsToken() {
        JwtService jwtService = new JwtService(new JwtProperties(SECRET, 3600, "tomato-test"));
        User user = User.builder()
                .id(7)
                .username("tomato")
                .build();

        String token = jwtService.generate(user);
        String tamperedToken = token.substring(0, token.length() - 1)
                + (token.endsWith("a") ? "b" : "a");

        assertThat(jwtService.isValid(tamperedToken)).isFalse();
        assertThatThrownBy(() -> jwtService.parse(tamperedToken)).isInstanceOf(RuntimeException.class);
    }
}
