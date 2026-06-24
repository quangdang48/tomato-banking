package com.tomato.modules.auth.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tomato.common.ApiResponse;
import com.tomato.exception.ErrorCode;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class AuthErrorResponseWriter {

    private final ObjectMapper objectMapper;

    public AuthErrorResponseWriter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public void writeUnauthorized(HttpServletResponse response) throws IOException {
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), ApiResponse.error(ErrorCode.ERROR_401_2201));
    }
}
