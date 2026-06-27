package com.example.User.Service.service;

import com.example.User.Service.dto.UserProfileResponse;

public interface UserIntegrationService {
    UserProfileResponse getFullUserProfile(Long userId);
}
