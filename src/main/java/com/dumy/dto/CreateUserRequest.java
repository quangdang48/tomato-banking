package com.dumy.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class CreateUserRequest {
    private String username;
    private String email;
    private String fullName;
    private String password;
}
