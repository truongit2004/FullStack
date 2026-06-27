package com.example.User.Service.service;

import com.example.User.Service.dto.UserCreateDto;
import com.example.User.Service.dto.UserDto;
import com.example.User.Service.entity.User;
import com.example.User.Service.exception.UserNotFoundException;
import com.example.User.Service.repository.UserRepository;
import com.example.User.Service.service.impl.UserServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class UserServiceImplTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private UserServiceImpl userService;

    private User userMock;

    @BeforeEach
    void setUp() {
        userMock = User.builder()
                .id(1L)
                .username("testuser")
                .email("test@gmail.com")
                .password("hashed_password")
                .firstName("Test")
                .lastName("User")
                .role("USER")
                .build();
    }

    @Test
    void testCreateUser_Success() {
        // Chuẩn bị dữ liệu đầu vào (Input)
        UserCreateDto request = new UserCreateDto();
        request.setUsername("testuser");
        request.setEmail("test@gmail.com");
        request.setPassword("123456");

        // Giả lập (Mock) hành động lưu vào DB
        when(userRepository.save(any(User.class))).thenReturn(userMock);

        // Chạy hàm cần test
        UserDto result = userService.createUser(request);

        // Kiểm tra kết quả (Assert)
        assertNotNull(result);
        assertEquals("testuser", result.getUsername());
        assertEquals("test@gmail.com", result.getEmail());
        verify(userRepository, times(1)).save(any(User.class)); // Đảm bảo hàm save() được gọi đúng 1 lần
    }

    @Test
    void testGetUserById_Success() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(userMock));

        UserDto result = userService.getUserById(1L);

        assertNotNull(result);
        assertEquals(1L, result.getId());
        assertEquals("Test", result.getFirstName());
    }

    @Test
    void testGetUserById_Exception_WhenUserNotFound() {
        // Giả lập DB không tìm thấy (Empty)
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        // Kiểm tra xem hệ thống có quăng đúng lỗi UserNotFoundException khi truyền ID 99 vào không
        assertThrows(UserNotFoundException.class, () -> userService.getUserById(99L));
    }
}
