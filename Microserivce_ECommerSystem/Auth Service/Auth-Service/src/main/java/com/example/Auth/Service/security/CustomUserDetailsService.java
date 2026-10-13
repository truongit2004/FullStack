package com.example.Auth.Service.security;

import com.example.Auth.Service.client.UserClient;
import com.example.Auth.Service.dto.UserDto;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final UserClient userClient;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        try {
            UserDto user = userClient.getUserByUsername(username);
            if (user == null) {
                throw new UsernameNotFoundException("User not found with username: " + username);
            }
            return new CustomUserDetails(user);
        } catch (UsernameNotFoundException e) {
            throw e;
        } catch (feign.FeignException.NotFound e) {
            throw new UsernameNotFoundException("User not found with username: " + username, e);
        } catch (Exception e) {
            // User Service chết / quá thời gian / ngắt mạch. Không được báo thành "không tìm thấy người dùng":
            // Spring Security sẽ đổi thành "sai mật khẩu" và người dùng tưởng mình nhập sai.
            throw new org.springframework.security.authentication.InternalAuthenticationServiceException(
                    "User Service unavailable", e);
        }
    }
}
