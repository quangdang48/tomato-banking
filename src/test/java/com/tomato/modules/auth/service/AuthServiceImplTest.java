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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

    @Mock
    private UserService userService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtService jwtService;

    private AuthServiceImpl authService;

    @BeforeEach
    void setUp() {
        authService = new AuthServiceImpl(
                userService,
                userRepository,
                passwordEncoder,
                jwtService,
                new JwtProperties("test-secret-minimum-32-bytes-long-value", 3600L, "tomato-test")
        );
    }

    @Test
    @DisplayName("Should delegate registration to the shared user creation path")
    void register_WithValidRequest_DelegatesToUserCreationPath() {
        RegisterRequest request = new RegisterRequest("tomato", "tomato@example.com", "Tom A", "secret123");
        User createdUser = User.builder().id(1).username("tomato").email("tomato@example.com").build();
        when(userService.createUser(new CreateUserRequest("tomato", "tomato@example.com", "Tom A", "secret123")))
                .thenReturn(createdUser);

        User user = authService.register(request);

        assertThat(user).isSameAs(createdUser);
        verify(userService).createUser(new CreateUserRequest("tomato", "tomato@example.com", "Tom A", "secret123"));
    }

    @Test
    @DisplayName("Should propagate duplicate username error from user creation path")
    void register_WithDuplicateUsername_ThrowsUsernameAlreadyTakenError() {
        RegisterRequest request = new RegisterRequest("tomato", "tomato@example.com", "Tom A", "secret123");
        CreateUserRequest createUserRequest = new CreateUserRequest("tomato", "tomato@example.com", "Tom A", "secret123");
        when(userService.createUser(createUserRequest))
                .thenThrow(new BusinessException(ErrorCode.ERROR_409_2002));

        assertThatThrownBy(() -> authService.register(request))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.ERROR_409_2002);
    }

    @Test
    @DisplayName("Should return a JWT token when credentials are valid")
    void login_WithValidCredentials_ReturnsToken() {
        LoginRequest request = new LoginRequest("tomato", "secret123");
        User user = User.builder()
                .id(1)
                .username("tomato")
                .passwordHash("$2a$hash")
                .build();
        when(userRepository.findByUsername("tomato")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("secret123", "$2a$hash")).thenReturn(true);
        when(jwtService.generate(user)).thenReturn("jwt-token");

        AuthResponse response = authService.login(request);

        assertThat(response.token()).isEqualTo("jwt-token");
        assertThat(response.expiresIn()).isEqualTo(3600L);
    }

    @Test
    @DisplayName("Should return invalid credentials when username is unknown")
    void login_WithUnknownUser_ThrowsInvalidCredentialsError() {
        when(userRepository.findByUsername("tomato")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(new LoginRequest("tomato", "secret123")))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.ERROR_401_2200);
        verifyNoInteractions(passwordEncoder, jwtService);
    }

    @Test
    @DisplayName("Should return the same invalid credentials error when password is wrong")
    void login_WithWrongPassword_ThrowsSameInvalidCredentialsError() {
        User user = User.builder().username("tomato").passwordHash("$2a$hash").build();
        when(userRepository.findByUsername("tomato")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong-password", "$2a$hash")).thenReturn(false);

        assertThatThrownBy(() -> authService.login(new LoginRequest("tomato", "wrong-password")))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.ERROR_401_2200);
        verifyNoInteractions(jwtService);
    }
}
