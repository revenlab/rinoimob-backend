package com.rinoimob.api.controller;

import com.rinoimob.context.TenantContext;
import com.rinoimob.domain.dto.ChangePasswordRequest;
import com.rinoimob.domain.dto.RequestEmailChangeRequest;
import com.rinoimob.domain.dto.UpdateProfileRequest;
import com.rinoimob.domain.dto.UserDto;
import com.rinoimob.domain.dto.UpdatePublicBrokerProfileRequest;
import com.rinoimob.domain.dto.PublicBrokerProfileResponse;
import com.rinoimob.domain.entity.User;
import com.rinoimob.domain.repository.UserRepository;
import com.rinoimob.service.auth.AuthService;
import com.rinoimob.service.storage.FileStorageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.Locale;

@RestController
@RequestMapping("/api/v1/users")
@Tag(name = "User Management", description = "User profile and account management endpoints")
@Slf4j
public class UserController {

    private final AuthService authService;
    private final UserRepository userRepository;
    private final FileStorageService fileStorageService;

    public UserController(AuthService authService, UserRepository userRepository, FileStorageService fileStorageService) {
        this.authService = authService;
        this.userRepository = userRepository;
        this.fileStorageService = fileStorageService;
    }

    @GetMapping
    @Operation(summary = "List active users for the current tenant")
    @PreAuthorize("hasAuthority('PERMISSION_users:read')")
    public ResponseEntity<List<UserDto>> listUsers(HttpServletRequest request) {
        UUID tenantId = (UUID) request.getAttribute("tenantId");
        if (tenantId == null) tenantId = UUID.fromString(TenantContext.getTenantId());
        List<User> users = userRepository.findByTenantIdAndActive(tenantId, true);
        List<UserDto> dtos = users.stream()
                .map(u -> new UserDto(
                        u.getId(),
                        u.getEmail(),
                        u.getFirstName(),
                        u.getLastName(),
                        u.getPhone(),
                        u.getActive(),
                        u.getSystemRole(),
                        u.getCreatedAt(),
                        Set.of()))
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
        UserDto updated = authService.updateUserProfile(userId, body.firstName(), body.lastName(), body.phone());
        return ResponseEntity.ok(updated);
    }

    @GetMapping("/profile/public")
    @Operation(summary = "Get the authenticated broker public profile")
    public ResponseEntity<PublicBrokerProfileResponse> getPublicProfile(HttpServletRequest request) {
        UUID userId = (UUID) request.getAttribute("userId");
        if (userId == null) throw unauthorized();
        User user = userRepository.findById(userId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        String name = String.join(" ", user.getFirstName() == null ? "" : user.getFirstName(), user.getLastName() == null ? "" : user.getLastName()).trim();
        return ResponseEntity.ok(toPublicBrokerProfile(user));
    }

    @PutMapping("/profile/public")
    @Operation(summary = "Update the authenticated broker public profile")
    public ResponseEntity<PublicBrokerProfileResponse> updatePublicProfile(
            @RequestBody UpdatePublicBrokerProfileRequest body,
            HttpServletRequest request) {
        UUID userId = (UUID) request.getAttribute("userId");
        if (userId == null) throw unauthorized();
        User user = userRepository.findById(userId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        String slug = body.slug() == null ? null : body.slug().trim().toLowerCase(Locale.ROOT);
        if (slug == null || !slug.matches("[a-z0-9]+(?:-[a-z0-9]+)*")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Use a slug with lowercase letters, numbers and hyphens");
        }
        userRepository.findByTenantIdAndPublicSlugAndActive(user.getTenantId(), slug, true)
                .filter(other -> !other.getId().equals(userId))
                .ifPresent(other -> { throw new ResponseStatusException(HttpStatus.CONFLICT, "This public slug is already in use"); });
        user.setPublicSlug(slug);
        user.setPublicBio(body.bio() == null || body.bio().isBlank() ? null : body.bio().trim());
        user.setPublicInstagramUrl(normalizeInstagramUrl(body.instagramUrl()));
        user.setCreci(body.creci() == null || body.creci().isBlank() ? null : body.creci().trim());
        userRepository.save(user);
        return ResponseEntity.ok(toPublicBrokerProfile(user));
    }

    @PostMapping(value = "/profile/public/photo", consumes = "multipart/form-data")
    @Operation(summary = "Upload the authenticated broker public photo")
    public ResponseEntity<PublicBrokerProfileResponse> uploadPublicProfilePhoto(
            @RequestPart("file") MultipartFile file, HttpServletRequest request) {
        UUID userId = (UUID) request.getAttribute("userId");
        if (userId == null) throw unauthorized();
        if (file.isEmpty() || file.getContentType() == null || !file.getContentType().startsWith("image/") || file.getSize() > 5L * 1024L * 1024L) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Upload an image up to 5 MB");
        }
        User user = userRepository.findById(userId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        FileStorageService.UploadResult upload = fileStorageService.upload(file);
        if (user.getPublicPhotoFid() != null) fileStorageService.delete(user.getPublicPhotoFid(), user.getPublicPhotoUrl());
        user.setPublicPhotoFid(upload.fid());
        user.setPublicPhotoUrl(upload.url());
        userRepository.save(user);
        return ResponseEntity.ok(toPublicBrokerProfile(user));
    }

    private PublicBrokerProfileResponse toPublicBrokerProfile(User user) {
        String name = String.join(" ", user.getFirstName() == null ? "" : user.getFirstName(), user.getLastName() == null ? "" : user.getLastName()).trim();
        return new PublicBrokerProfileResponse(user.getPublicSlug(), name, user.getPhone(), user.getPublicBio(), user.getPublicPhotoUrl(), user.getPublicInstagramUrl(), user.getCreci());
    }

    private String normalizeInstagramUrl(String value) {
        if (value == null || value.isBlank()) return null;
        String normalized = value.trim();
        if (normalized.startsWith("@")) normalized = "https://instagram.com/" + normalized.substring(1);
        if (!normalized.startsWith("https://instagram.com/") && !normalized.startsWith("https://www.instagram.com/")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Use a valid Instagram URL or @handle");
        }
        return normalized.replaceAll("/+$", "");
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

    @PostMapping("/request-email-change")
    @Operation(summary = "Request an email address change")
    public ResponseEntity<Void> requestEmailChange(
            @RequestBody @Valid RequestEmailChangeRequest body,
            HttpServletRequest request) {
        UUID userId = (UUID) request.getAttribute("userId");
        if (userId == null) {
            throw unauthorized();
        }
        authService.requestEmailChange(userId, body.newEmail(), body.currentPassword());
        return ResponseEntity.accepted().build();
    }

    private ResponseStatusException unauthorized() {
        return new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unauthorized");
    }
}
