package com.tomato.modules.user.service;

import com.tomato.exception.BusinessException;
import com.tomato.exception.ErrorCode;
import com.tomato.modules.user.dto.request.CreateUserRequest;
import com.tomato.modules.user.entity.User;
import com.tomato.modules.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceImplTest {

    private static final CreateUserRequest CREATE_USER_REQUEST = new CreateUserRequest(
            "tomato",
            "tomato@example.com",
            "Tom A",
            "secret123"
    );

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private UserServiceImpl userService;

    @Test
    @DisplayName("Should reject user creation when username already exists")
    void createUser_WithDuplicateUsername_ThrowsUsernameAlreadyTakenError() {
        when(userRepository.findByUsername("tomato")).thenReturn(Optional.of(User.builder().build()));

        assertThatThrownBy(() -> userService.createUser(CREATE_USER_REQUEST))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.ERROR_409_2002);
    }

    @Test
    @DisplayName("Should reject user creation when email already exists")
    void createUser_WithDuplicateEmail_ThrowsEmailAlreadyInUseError() {
        when(userRepository.findByUsername("tomato")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("tomato@example.com")).thenReturn(Optional.of(User.builder().build()));

        assertThatThrownBy(() -> userService.createUser(CREATE_USER_REQUEST))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.ERROR_409_2003);
    }

    @Test
    @DisplayName("Should store BCrypt hash when creating a user")
    void createUser_WithValidRequest_StoresPasswordHash() {
        when(userRepository.findByUsername("tomato")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("tomato@example.com")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("secret123")).thenReturn("$2a$hash");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        User user = userService.createUser(CREATE_USER_REQUEST);

        assertThat(user.getPasswordHash()).isEqualTo("$2a$hash");
        assertThat(user.getPasswordHash()).isNotEqualTo("secret123");
        verify(passwordEncoder).encode("secret123");
    }
}
