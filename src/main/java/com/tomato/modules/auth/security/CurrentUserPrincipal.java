package com.tomato.modules.auth.security;

import com.tomato.exception.BusinessException;
import com.tomato.exception.ErrorCode;
import org.springframework.security.core.AuthenticatedPrincipal;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Authenticated caller derived from the validated JWT (the {@code uid} claim and subject).
 * Stored as the Spring Security principal, so controllers can inject it directly with
 * {@code @AuthenticationPrincipal CurrentUserPrincipal user}.
 */
public record CurrentUserPrincipal(Integer userId, String username) implements AuthenticatedPrincipal {

    @Override
    public String getName() {
        return username;
    }

    /**
     * Returns the principal in the current security context, or throws {@code ERROR_401_2201}
     * when the request has no JWT-derived principal. For use outside controllers (e.g. guards);
     * controllers should prefer {@code @AuthenticationPrincipal}.
     */
    public static CurrentUserPrincipal require() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof CurrentUserPrincipal principal)) {
            throw new BusinessException(ErrorCode.ERROR_401_2201);
        }
        return principal;
    }
}
