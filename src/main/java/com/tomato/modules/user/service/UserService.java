package com.tomato.modules.user.service;

import com.tomato.modules.user.dto.request.CreateUserRequest;
import com.tomato.modules.user.dto.request.UpdateUserRequest;
import com.tomato.modules.user.entity.User;

import java.util.List;

public interface UserService {
    User createUser(CreateUserRequest request);

    User getUserById(Integer id);

    List<User> getAllUsers();

    User updateUser(Integer id, UpdateUserRequest request);

    void deleteUser(Integer id);
}
