package com.example.User.Service.service;

import com.example.User.Service.dto.UpdatePasswordRequest;
import com.example.User.Service.dto.UpdateProfileRequest;
import com.example.User.Service.dto.UserCreateDto;
import com.example.User.Service.dto.UserDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface UserService {
    UserDto createUser(UserCreateDto request);
    UserDto getUserById(Long id);
    UserDto getUserByEmail(String email);
    com.example.User.Service.dto.AuthUserDto getAuthUserByUsername(String username);
    Page<UserDto> getAllUsers(Pageable pageable);
    void deleteUser(Long id);
    UserDto updateProfile(Long id, UpdateProfileRequest request);
    void updatePassword(Long id, UpdatePasswordRequest request);
}
