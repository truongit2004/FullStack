package com.example.User.Service.service;

import com.example.User.Service.dto.UserCreateDto;
import com.example.User.Service.dto.UserDto;
import java.util.List;

public interface UserService {
    UserDto createUser(UserCreateDto request);
    UserDto getUserById(Long id);
    UserDto getUserByEmail(String email);
    com.example.User.Service.dto.AuthUserDto getAuthUserByUsername(String username);
    List<UserDto> getAllUsers();
    void deleteUser(Long id);
}
