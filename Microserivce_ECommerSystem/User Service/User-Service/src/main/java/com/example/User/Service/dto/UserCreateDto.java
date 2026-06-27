package com.example.User.Service.dto;

import lombok.Data;

@Data
public class UserCreateDto {
    private String username;
    private String email;
    private String password;
    private String firstName;
    private String lastName;
    private String role;
}
