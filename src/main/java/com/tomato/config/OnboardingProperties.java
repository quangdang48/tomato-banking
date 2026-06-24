package com.tomato.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * Temporary onboarding reviewer gate. Until a real role model exists, reviewer endpoints
 * are restricted to the usernames listed here. An empty/absent list permits any
 * authenticated user (dev/admin testing only).
 */
@ConfigurationProperties(prefix = "app.onboarding")
public record OnboardingProperties(List<String> reviewerUsernames) {
}
