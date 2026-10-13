package com.example.User.Service.controller;

import com.example.User.Service.dto.UpdatePasswordRequest;
import com.example.User.Service.dto.UpdateProfileRequest;
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
    public ResponseEntity<org.springframework.data.domain.Page<UserDto>> getAllUsers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        org.springframework.data.domain.Pageable pageable = org.springframework.data.domain.PageRequest.of(page, size);
        return ResponseEntity.ok(userService.getAllUsers(pageable));
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

    @PutMapping("/{id}")
    public ResponseEntity<UserDto> updateProfile(
            @PathVariable Long id,
            @RequestBody UpdateProfileRequest request,
            @RequestHeader(value = "X-User-Id", required = false) String requesterId,
            @RequestHeader(value = "X-User-Role", required = false) String requesterRole) {
        assertOwnerOrAdmin(id, requesterId, requesterRole);
        return ResponseEntity.ok(userService.updateProfile(id, request));
    }

    @PutMapping("/{id}/password")
    public ResponseEntity<Void> updatePassword(
            @PathVariable Long id,
            @RequestBody UpdatePasswordRequest request,
            @RequestHeader(value = "X-User-Id", required = false) String requesterId,
            @RequestHeader(value = "X-User-Role", required = false) String requesterRole) {
        assertOwnerOrAdmin(id, requesterId, requesterRole);
        userService.updatePassword(id, request);
        return ResponseEntity.noContent().build();
    }

    // Chỉ enforce khi request đi qua API Gateway (có header X-User-Id).
    // Lời gọi nội bộ giữa các service (vd. Auth Service qua Feign) không mang header này
    // và được tin cậy theo mô hình bảo mật hiện có của hệ thống (xem SecurityConfig permitAll).
    private void assertOwnerOrAdmin(Long targetId, String requesterId, String requesterRole) {
        if (requesterId == null) {
            return;
        }
        boolean isAdmin = "ADMIN".equalsIgnoreCase(requesterRole);
        boolean isOwner = String.valueOf(targetId).equals(requesterId);
        if (!isAdmin && !isOwner) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.FORBIDDEN, "You can only modify your own account");
        }
    }
}
