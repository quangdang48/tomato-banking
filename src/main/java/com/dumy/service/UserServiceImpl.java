package com.dumy.service;

import com.dumy.dto.CreateUserRequest;
import com.dumy.dto.UpdateUserRequest;
import com.dumy.entity.User;
import com.dumy.exception.ErrorCode;
import com.dumy.exception.ObjectsValidator;
import com.dumy.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;

    @Override
    public User createUser(CreateUserRequest request) {
        ObjectsValidator.mustNull(
                userRepository.findByUsername(request.getUsername()).orElse(null),
                ErrorCode.ERROR_409_2002
        );
        ObjectsValidator.mustNull(
                userRepository.findByEmail(request.getEmail()).orElse(null),
                ErrorCode.ERROR_409_2003
        );

        User user = User.builder()
                .username(request.getUsername())
                .email(request.getEmail())
                .fullName(request.getFullName())
                .passwordHash(request.getPassword())
                .build();
        return userRepository.save(user);
    }

    @Override
    public User getUserById(Integer id) {
        User user = userRepository.findById(id).orElse(null);
        ObjectsValidator.mustNotNull(user, ErrorCode.ERROR_404_2001);
        return user;
    }

    @Override
    public List<User> getAllUsers() {
        return userRepository.findAll();
    }

    @Override
    public User updateUser(Integer id, UpdateUserRequest request) {
        User user = userRepository.findById(id).orElse(null);
        ObjectsValidator.mustNotNull(user, ErrorCode.ERROR_404_2001);
        user.setFullName(request.getFullName());
        user.setEmail(request.getEmail());
        return userRepository.save(user);
    }

    @Override
    public void deleteUser(Integer id) {
        User user = userRepository.findById(id).orElse(null);
        ObjectsValidator.mustNotNull(user, ErrorCode.ERROR_404_2001);
        userRepository.deleteById(id);
    }
}
