package com.rinoimob.api.controller;

import com.rinoimob.context.TenantContext;
import com.rinoimob.domain.dto.ChangePasswordRequest;
import com.rinoimob.domain.dto.UpdateProfileRequest;
import com.rinoimob.domain.dto.UserDto;
import com.rinoimob.domain.entity.User;
import com.rinoimob.domain.repository.UserRepository;
import com.rinoimob.service.auth.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/users")
@Tag(name = "User Management", description = "User profile and account management endpoints")
@Slf4j
public class UserController {

    private final AuthService authService;
    private final UserRepository userRepository;

    public UserController(AuthService authService, UserRepository userRepository) {
        this.authService = authService;
        this.userRepository = userRepository;
    }

    @GetMapping
    @Operation(summary = "List active users for the current tenant")
    public ResponseEntity<List<UserDto>> listUsers(HttpServletRequest request) {
        UUID tenantId = (UUID) request.getAttribute("tenantId");
        if (tenantId == null) tenantId = UUID.fromString(TenantContext.getTenantId());
        List<User> users = userRepository.findByTenantIdAndActive(tenantId, true);
        List<UserDto> dtos = users.stream()
                .map(u -> new UserDto(u.getId(), u.getEmail(), u.getFirstName(), u.getLastName(), u.getActive(), u.getCreatedAt()))
                .toList();
        return ResponseEntity.ok(dtos);
    }

    @GetMapping("/profile")
    @Operation(summary = "Get user profile")
    public ResponseEntity<UserDto> getProfile(HttpServletRequest request) {
        UUID userId = (UUID) request.getAttribute("userId");
        if (userId == null) {
            throw unauthorized();
        }
        UserDto profile = authService.getUserProfile(userId);
        return ResponseEntity.ok(profile);
    }

    @PutMapping("/profile")
    @Operation(summary = "Update user profile")
    public ResponseEntity<UserDto> updateProfile(
            @RequestBody @Valid UpdateProfileRequest body,
            HttpServletRequest request) {
        UUID userId = (UUID) request.getAttribute("userId");
        if (userId == null) {
            throw unauthorized();
        }
        UserDto updated = authService.updateUserProfile(userId, body.firstName(), body.lastName());
        return ResponseEntity.ok(updated);
    }

    @PostMapping("/change-password")
    @Operation(summary = "Change user password")
    public ResponseEntity<Void> changePassword(
            @RequestBody @Valid ChangePasswordRequest body,
            HttpServletRequest request) {
        UUID userId = (UUID) request.getAttribute("userId");
        if (userId == null) {
            throw unauthorized();
        }
        authService.changePassword(userId, body.currentPassword(), body.newPassword(), body.confirmPassword());
        return ResponseEntity.ok().build();
    }

    private ResponseStatusException unauthorized() {
        return new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unauthorized");
    }
}
