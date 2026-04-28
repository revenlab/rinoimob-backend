package com.rinoimob.api.controller;

import com.rinoimob.domain.dto.ForgotPasswordRequest;
import com.rinoimob.domain.dto.IdentifyRequest;
import com.rinoimob.domain.dto.IdentifyResponse;
import com.rinoimob.domain.dto.LoginRequest;
import com.rinoimob.domain.dto.LoginResponse;
import com.rinoimob.domain.dto.MeResponse;
import com.rinoimob.domain.dto.PasswordResetRequest;
import com.rinoimob.domain.dto.RegisterRequest;
import com.rinoimob.domain.dto.SelectTenantRequest;
import com.rinoimob.domain.dto.TenantRegistrationRequest;
import com.rinoimob.domain.entity.Tenant;
import com.rinoimob.service.TenantService;
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

import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Authentication", description = "User authentication and authorization endpoints")
@Slf4j
public class AuthController {

    private final AuthService authService;
    private final TenantService tenantService;

    public AuthController(AuthService authService, TenantService tenantService) {
        this.authService = authService;
        this.tenantService = tenantService;
    }

    @GetMapping("/me")
    @Operation(summary = "Get current authenticated user and validate session")
    public ResponseEntity<MeResponse> me(HttpServletRequest request) {
        UUID userId = (UUID) request.getAttribute("userId");
        if (userId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unauthorized");
        }
        return ResponseEntity.ok(authService.getMe(userId));
    }

    @PostMapping("/signup")
    @Operation(summary = "Sign up as a new tenant owner")
    public ResponseEntity<?> signup(@Valid @RequestBody TenantRegistrationRequest request) {
        authService.signup(request);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @PostMapping("/register")
    @Operation(summary = "Register a new user within the current tenant")
    public ResponseEntity<?> register(@Valid @RequestBody RegisterRequest request) {
        Optional<Tenant> tenantOpt = tenantService.getCurrentTenant();
        if (tenantOpt.isEmpty()) {
            throw tenantNotResolvedBadRequest();
        }
        authService.register(request, tenantOpt.get().getId());
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @PostMapping("/identify")
    @Operation(summary = "Step 1 of workspace-selector login: validate credentials and list workspaces")
    public ResponseEntity<IdentifyResponse> identify(@Valid @RequestBody IdentifyRequest request) {
        IdentifyResponse response = authService.identify(request.email(), request.password());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/select-tenant")
    @Operation(summary = "Step 2 of workspace-selector login: select workspace and obtain JWT")
    public ResponseEntity<LoginResponse> selectTenant(@Valid @RequestBody SelectTenantRequest request) {
        LoginResponse response = authService.selectTenant(request.preAuthToken(), request.tenantId());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/login")
    @Operation(summary = "Login via host-resolved tenant (used by client website)")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest request) {
        Optional<Tenant> tenantOpt = tenantService.getCurrentTenant();
        if (tenantOpt.isEmpty()) {
            throw tenantNotResolvedBadRequest();
        }
        LoginResponse response = authService.login(request, tenantOpt.get().getId());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/verify-email")
    @Operation(summary = "Verify email with token")
    public ResponseEntity<Void> verifyEmail(@RequestParam String token) {
        authService.verifyEmail(token);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/forgot-password")
    @Operation(summary = "Request password reset")
    public ResponseEntity<?> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        authService.requestPasswordReset(request.email());
        return ResponseEntity.ok().build();
    }

    @PostMapping("/reset-password")
    @Operation(summary = "Reset password with token")
    public ResponseEntity<Void> resetPassword(@Valid @RequestBody PasswordResetRequest request) {
        authService.resetPassword(request.token(), request.password(), request.confirmPassword());
        return ResponseEntity.ok().build();
    }

    private ResponseStatusException tenantNotResolvedBadRequest() {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tenant context not found");
    }
}

