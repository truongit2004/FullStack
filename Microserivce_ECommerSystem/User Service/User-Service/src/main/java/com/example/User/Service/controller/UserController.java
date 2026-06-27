package com.example.User.Service.controller;

import com.example.User.Service.dto.UserCreateDto;
import com.example.User.Service.dto.UserDto;
import com.example.User.Service.dto.UserProfileResponse;
import com.example.User.Service.service.UserIntegrationService;
import com.example.User.Service.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final UserIntegrationService userIntegrationService;

    @PostMapping
    public ResponseEntity<UserDto> createUser(@RequestBody UserCreateDto request) {
        return new ResponseEntity<>(userService.createUser(request), HttpStatus.CREATED);
    }

    @GetMapping("/{id}")
    public ResponseEntity<UserDto> getUserById(@PathVariable Long id) {
        return ResponseEntity.ok(userService.getUserById(id));
    }

    @GetMapping("/{id}/profile-full")
    public ResponseEntity<UserProfileResponse> getFullUserProfile(@PathVariable Long id) {
        return ResponseEntity.ok(userIntegrationService.getFullUserProfile(id));
    }

    @GetMapping("/email/{email}")
    public ResponseEntity<UserDto> getUserByEmail(@PathVariable String email) {
        return ResponseEntity.ok(userService.getUserByEmail(email));
    }

    @GetMapping
    public ResponseEntity<List<UserDto>> getAllUsers() {
        return ResponseEntity.ok(userService.getAllUsers());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteUser(@PathVariable Long id) {
        userService.deleteUser(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/username/{username}/auth")
    public ResponseEntity<com.example.User.Service.dto.AuthUserDto> getAuthUserByUsername(@PathVariable String username) {
        return ResponseEntity.ok(userService.getAuthUserByUsername(username));
    }
}
