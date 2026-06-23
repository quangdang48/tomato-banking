package com.tomato.service;

import com.tomato.dto.CreateUserRequest;
import com.tomato.dto.UpdateUserRequest;
import com.tomato.entity.User;

import java.util.List;

public interface UserService {
    User createUser(CreateUserRequest request);

    User getUserById(Integer id);

    List<User> getAllUsers();

    User updateUser(Integer id, UpdateUserRequest request);

    void deleteUser(Integer id);
}
