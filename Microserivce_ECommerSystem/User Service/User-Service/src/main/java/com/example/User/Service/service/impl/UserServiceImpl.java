package com.example.User.Service.service.impl;

import com.example.User.Service.dto.UpdatePasswordRequest;
import com.example.User.Service.dto.UpdateProfileRequest;
import com.example.User.Service.dto.UserCreateDto;
import com.example.User.Service.dto.UserDto;
import com.example.User.Service.dto.AuthUserDto;
import com.example.User.Service.entity.User;
import com.example.User.Service.exception.UserNotFoundException;
import com.example.User.Service.repository.UserRepository;
import com.example.User.Service.service.UserService;
import com.example.User.Service.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserMapper userMapper;

    @Override
    public UserDto createUser(UserCreateDto request) {
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new RuntimeException("Username already exists");
        }
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new RuntimeException("Email already exists");
        }

        User user = userMapper.toEntity(request, passwordEncoder);
        user = userRepository.save(user);
        return userMapper.toDto(user);
    }

    @Override
    @Cacheable(value = "users", key = "#id")
    public UserDto getUserById(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new UserNotFoundException("User not found with id: " + id));
        return userMapper.toDto(user);
    }

    @Override
    @Cacheable(value = "usersByEmail", key = "#email")
    public UserDto getUserByEmail(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UserNotFoundException("User not found with email: " + email));
        return userMapper.toDto(user);
    }

    @Override
    @Cacheable(value = "authUsers", key = "#username")
    public AuthUserDto getAuthUserByUsername(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UserNotFoundException("User not found with username: " + username));
        return userMapper.toAuthUserDto(user);
    }

    @Override
    public Page<UserDto> getAllUsers(Pageable pageable) {
        return userRepository.findAll(pageable)
                .map(userMapper::toDto);
    }

    @Override
    @Caching(evict = {
            @CacheEvict(value = "users", key = "#id"),
            @CacheEvict(value = "usersByEmail", allEntries = true),
            @CacheEvict(value = "authUsers", allEntries = true)
    })
    public void deleteUser(Long id) {
        if (!userRepository.existsById(id)) {
            throw new UserNotFoundException("User not found with id: " + id);
        }
        userRepository.deleteById(id);
    }

    @Override
    @Caching(evict = {
            @CacheEvict(value = "users", key = "#id"),
            @CacheEvict(value = "usersByEmail", allEntries = true),
            @CacheEvict(value = "authUsers", allEntries = true)
    })
    public UserDto updateProfile(Long id, UpdateProfileRequest request) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new UserNotFoundException("User not found with id: " + id));

        if (request.getFirstName() != null && !request.getFirstName().isBlank()) {
            user.setFirstName(request.getFirstName());
        }
        if (request.getLastName() != null && !request.getLastName().isBlank()) {
            user.setLastName(request.getLastName());
        }
        if (request.getEmail() != null && !request.getEmail().isBlank() && !request.getEmail().equals(user.getEmail())) {
            if (userRepository.existsByEmail(request.getEmail())) {
                throw new RuntimeException("Email already in use");
            }
            user.setEmail(request.getEmail());
        }

        user = userRepository.save(user);
        return userMapper.toDto(user);
    }

    @Override
    @Caching(evict = {
            @CacheEvict(value = "users", key = "#id"),
            @CacheEvict(value = "authUsers", allEntries = true)
    })
    public void updatePassword(Long id, UpdatePasswordRequest request) {
        if (request.getNewPassword() == null || request.getNewPassword().isBlank()) {
            throw new RuntimeException("New password must not be blank");
        }
        User user = userRepository.findById(id)
                .orElseThrow(() -> new UserNotFoundException("User not found with id: " + id));
        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);
    }
}
