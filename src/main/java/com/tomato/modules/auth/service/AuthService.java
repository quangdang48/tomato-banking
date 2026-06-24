package com.tomato.modules.auth.service;

import com.tomato.modules.auth.dto.request.LoginRequest;
import com.tomato.modules.auth.dto.request.RegisterRequest;
import com.tomato.modules.auth.dto.response.AuthResponse;
import com.tomato.modules.auth.dto.response.RegistrationResponseDto;

public interface AuthService {
    RegistrationResponseDto register(RegisterRequest request);

    AuthResponse login(LoginRequest request);
}
