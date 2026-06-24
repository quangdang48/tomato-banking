package com.tomato.modules.user.service;

import com.tomato.exception.ErrorCode;
import com.tomato.exception.ObjectsValidator;
import com.tomato.modules.user.dto.request.CreateUserRequest;
import com.tomato.modules.user.dto.request.UpdateUserRequest;
import com.tomato.modules.user.entity.User;
import com.tomato.modules.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public User createUser(CreateUserRequest request) {
        ObjectsValidator.mustNull(
                userRepository.findByUsername(request.username()).orElse(null),
                ErrorCode.ERROR_409_2002
        );
        ObjectsValidator.mustNull(
                userRepository.findByEmail(request.email()).orElse(null),
                ErrorCode.ERROR_409_2003
        );

        User user = User.builder()
                .username(request.username())
                .email(request.email())
                .fullName(request.fullName())
                .passwordHash(passwordEncoder.encode(request.password()))
                .build();
        return userRepository.save(user);
    }

    @Override
    @Transactional(readOnly = true)
    public User getUserById(Integer id) {
        User user = userRepository.findById(id).orElse(null);
        ObjectsValidator.mustNotNull(user, ErrorCode.ERROR_404_2001);
        return user;
    }

    @Override
    @Transactional(readOnly = true)
    public List<User> getAllUsers() {
        return userRepository.findAll();
    }

    @Override
    @Transactional
    public User updateUser(Integer id, UpdateUserRequest request) {
        User user = userRepository.findById(id).orElse(null);
        ObjectsValidator.mustNotNull(user, ErrorCode.ERROR_404_2001);
        user.setFullName(request.fullName());
        user.setEmail(request.email());
        return userRepository.save(user);
    }

    @Override
    @Transactional
    public void deleteUser(Integer id) {
        User user = userRepository.findById(id).orElse(null);
        ObjectsValidator.mustNotNull(user, ErrorCode.ERROR_404_2001);
        userRepository.deleteById(id);
    }
}
