package com.rinoimob.api.controller;

import com.rinoimob.domain.dto.UserDto;
import com.rinoimob.service.auth.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/users")
@Tag(name = "User Management", description = "User profile and account management endpoints")
@Slf4j
public class UserController {

    private final AuthService authService;

    public UserController(AuthService authService) {
        this.authService = authService;
    }

    @GetMapping("/profile")
    @Operation(summary = "Get user profile")
    public ResponseEntity<UserDto> getProfile(HttpServletRequest request) {
        UUID userId = (UUID) request.getAttribute("userId");
        UserDto profile = authService.getUserProfile(userId);
        return ResponseEntity.ok(profile);
    }

    @PutMapping("/profile")
    @Operation(summary = "Update user profile")
    public ResponseEntity<UserDto> updateProfile(
            @RequestParam String firstName,
            @RequestParam String lastName,
            HttpServletRequest request) {
        UUID userId = (UUID) request.getAttribute("userId");
        UserDto updated = authService.updateUserProfile(userId, firstName, lastName);
        return ResponseEntity.ok(updated);
    }

    @PostMapping("/change-password")
    @Operation(summary = "Change user password")
    public ResponseEntity<Void> changePassword(
            @RequestParam String currentPassword,
            @RequestParam String newPassword,
            @RequestParam String confirmPassword,
            HttpServletRequest request) {
        UUID userId = (UUID) request.getAttribute("userId");
        authService.changePassword(userId, currentPassword, newPassword, confirmPassword);
        return ResponseEntity.ok().build();
    }
}
