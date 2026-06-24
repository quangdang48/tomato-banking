package com.tomato.modules.auth.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tomato.exception.ErrorCode;
import com.tomato.modules.auth.dto.request.LoginRequest;
import com.tomato.modules.auth.dto.request.RegisterRequest;
import com.tomato.modules.auth.service.JwtService;
import com.tomato.modules.user.entity.User;
import com.tomato.modules.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AuthSecurityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
    }

    @Test
    @DisplayName("Should allow public registration and return created envelope")
    void register_WithoutToken_ReturnsCreatedEnvelope() throws Exception {
        RegisterRequest request = new RegisterRequest("tomato", "tomato@example.com", "Tom A", "secret123");

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.username").value("tomato"))
                .andExpect(jsonPath("$.data.email").value("tomato@example.com"))
                .andExpect(jsonPath("$.data.passwordHash").doesNotExist());
    }

    @Test
    @DisplayName("Should reject registration when password is too short")
    void register_WithShortPassword_ReturnsValidationErrorEnvelope() throws Exception {
        RegisterRequest request = new RegisterRequest("tomato", "tomato@example.com", "Tom A", "short");

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.ERROR_400_VALIDATION.getCode()))
                .andExpect(jsonPath("$.message").value("Password must be between 8 and 100 characters"));
    }

    @Test
    @DisplayName("Should reject registration when username already exists")
    void register_WithDuplicateUsername_ReturnsConflictEnvelope() throws Exception {
        saveUser("tomato", "existing@example.com", "secret123");
        RegisterRequest request = new RegisterRequest("tomato", "tomato@example.com", "Tom A", "secret123");

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(ErrorCode.ERROR_409_2002.getCode()))
                .andExpect(jsonPath("$.message").value(ErrorCode.ERROR_409_2002.getMessage()));
    }

    @Test
    @DisplayName("Should allow public login and return token envelope")
    void login_WithoutToken_ReturnsTokenEnvelope() throws Exception {
        saveUser("tomato", "tomato@example.com", "secret123");

        LoginRequest request = new LoginRequest("tomato", "secret123");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.token", not(nullValue())))
                .andExpect(jsonPath("$.data.expiresIn").value(3600));
    }

    @Test
    @DisplayName("Should ignore invalid bearer token on public login route")
    void login_WithInvalidTokenOnPublicRoute_ReturnsTokenEnvelope() throws Exception {
        saveUser("tomato", "tomato@example.com", "secret123");
        LoginRequest request = new LoginRequest("tomato", "secret123");

        mockMvc.perform(post("/api/auth/login")
                        .header("Authorization", "Bearer invalid-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.token", not(nullValue())));
    }

    @Test
    @DisplayName("Should reject login when password is wrong")
    void login_WithWrongPassword_ReturnsInvalidCredentialsEnvelope() throws Exception {
        saveUser("tomato", "tomato@example.com", "secret123");
        LoginRequest request = new LoginRequest("tomato", "wrong-password");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(ErrorCode.ERROR_401_2200.getCode()))
                .andExpect(jsonPath("$.message").value(ErrorCode.ERROR_401_2200.getMessage()));
    }

    @Test
    @DisplayName("Should reject login when username is blank")
    void login_WithBlankUsername_ReturnsValidationErrorEnvelope() throws Exception {
        LoginRequest request = new LoginRequest("", "secret123");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.ERROR_400_VALIDATION.getCode()))
                .andExpect(jsonPath("$.message").value("Username is required"));
    }

    @Test
    @DisplayName("Should reject protected route when token is missing")
    void getUsers_WithoutToken_ReturnsUnauthorizedEnvelope() throws Exception {
        mockMvc.perform(get("/api/users"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(ErrorCode.ERROR_401_2201.getCode()))
                .andExpect(jsonPath("$.message").value(ErrorCode.ERROR_401_2201.getMessage()));
    }

    @Test
    @DisplayName("Should reject protected route when token is invalid")
    void getUsers_WithInvalidToken_ReturnsUnauthorizedEnvelope() throws Exception {
        mockMvc.perform(get("/api/users")
                        .header("Authorization", "Bearer invalid-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(ErrorCode.ERROR_401_2201.getCode()))
                .andExpect(jsonPath("$.message").value(ErrorCode.ERROR_401_2201.getMessage()));
    }

    @Test
    @DisplayName("Should reject protected route when authorization header is not bearer")
    void getUsers_WithNonBearerAuthorization_ReturnsUnauthorizedEnvelope() throws Exception {
        mockMvc.perform(get("/api/users")
                        .header("Authorization", "Basic abc123"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(ErrorCode.ERROR_401_2201.getCode()))
                .andExpect(jsonPath("$.message").value(ErrorCode.ERROR_401_2201.getMessage()));
    }

    @Test
    @DisplayName("Should allow protected route when token is valid")
    void getUsers_WithValidToken_ReturnsSuccessEnvelope() throws Exception {
        User user = saveUser("tomato", "tomato@example.com", "secret123");
        String token = jwtService.generate(user);

        mockMvc.perform(get("/api/users")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }

    private User saveUser(String username, String email, String password) {
        return userRepository.save(User.builder()
                .username(username)
                .email(email)
                .fullName("Tom A")
                .passwordHash(passwordEncoder.encode(password))
                .build());
    }
}
