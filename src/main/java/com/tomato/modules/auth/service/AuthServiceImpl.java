package com.tomato.modules.auth.service;

import com.tomato.config.JwtProperties;
import com.tomato.exception.BusinessException;
import com.tomato.exception.ErrorCode;
import com.tomato.modules.auth.dto.request.LoginRequest;
import com.tomato.modules.auth.dto.request.RegisterRequest;
import com.tomato.modules.auth.dto.response.AuthResponse;
import com.tomato.modules.user.dto.request.CreateUserRequest;
import com.tomato.modules.user.entity.User;
import com.tomato.modules.user.repository.UserRepository;
import com.tomato.modules.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UserService userService;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final JwtProperties jwtProperties;

    @Override
    @Transactional
    public User register(RegisterRequest request) {
        return userService.createUser(new CreateUserRequest(
                request.username(),
                request.email(),
                request.fullName(),
                request.password()
        ));
    }

    @Override
    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByUsername(request.username()).orElse(null);
        if (user == null || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new BusinessException(ErrorCode.ERROR_401_2200);
        }

        String token = jwtService.generate(user);
        return AuthResponse.of(token, jwtProperties.expiresIn());
    }
}
