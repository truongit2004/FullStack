package com.example.Auth.Service.client;

import com.example.Auth.Service.dto.UserDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "USER-SERVICE", url = "http://localhost:8088") // Fallback url during dev or load balanced via Gateway
public interface UserClient {

    @GetMapping("/api/users/username/{username}/auth")
    UserDto getUserByUsername(@PathVariable("username") String username);

    @PostMapping("/api/users")
    UserDto createUser(@RequestBody UserDto userDto);
}
