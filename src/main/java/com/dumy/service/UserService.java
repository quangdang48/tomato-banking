package com.dumy.service;

import com.dumy.dto.CreateUserRequest;
import com.dumy.dto.UpdateUserRequest;
import com.dumy.entity.User;

import java.util.List;

public interface UserService {
    User createUser(CreateUserRequest request);

    User getUserById(Integer id);

    List<User> getAllUsers();

    User updateUser(Integer id, UpdateUserRequest request);

    void deleteUser(Integer id);
}
