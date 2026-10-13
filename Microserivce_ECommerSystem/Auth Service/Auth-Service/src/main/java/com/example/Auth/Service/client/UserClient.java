package com.example.Auth.Service.client;

import com.example.Auth.Service.dto.UpdatePasswordRequest;
import com.example.Auth.Service.dto.UserDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;

@FeignClient(name = "USER-SERVICE", url = "http://localhost:8088") // Fallback url during dev or load balanced via Gateway
// Ngắt mạch: service này lỗi liên tục thì tạm ngừng gọi, trả lỗi ngay thay vì chờ hết timeout
@CircuitBreaker(name = "userService")
public interface UserClient {

    @Retry(name = "userService")
    @GetMapping("/api/users/username/{username}/auth")
    UserDto getUserByUsername(@PathVariable("username") String username);

    @Retry(name = "userService")
    @GetMapping("/api/users/email/{email}")
    UserDto getUserByEmail(@PathVariable("email") String email);

    @PostMapping("/api/users")
    UserDto createUser(@RequestBody UserDto userDto);

    @PutMapping("/api/users/{id}/password")
    void updatePassword(@PathVariable("id") Long id, @RequestBody UpdatePasswordRequest request);
}
