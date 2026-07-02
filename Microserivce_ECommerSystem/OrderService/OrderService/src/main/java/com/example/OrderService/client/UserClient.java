package com.example.OrderService.client;

import com.example.OrderService.dto.UserResponseDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "user-service", path = "/api/users")
public interface UserClient {

    @GetMapping("/{id}")
    UserResponseDTO getUserById(@PathVariable("id") String id); // Dùng String để khớp với UUID hoặc Long bên User Service
}
