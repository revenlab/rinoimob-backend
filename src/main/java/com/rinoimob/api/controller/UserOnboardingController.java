package com.rinoimob.api.controller;

import com.rinoimob.context.TenantContext;
import com.rinoimob.domain.dto.OnboardingSummaryResponse;
import com.rinoimob.domain.dto.UpdateOnboardingProgressRequest;
import com.rinoimob.service.onboarding.UserOnboardingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/users/onboarding")
@RequiredArgsConstructor
@Tag(name = "User Onboarding", description = "Tutorial state for the authenticated user")
public class UserOnboardingController {

    private final UserOnboardingService userOnboardingService;

    @PutMapping("/{tutorialKey}")
    @Operation(summary = "Create or update onboarding progress for the authenticated user")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Onboarding progress updated"),
            @ApiResponse(responseCode = "401", description = "Unauthorized", content = @Content)
    })
    public ResponseEntity<OnboardingSummaryResponse> updateProgress(@PathVariable String tutorialKey,
                                                                    @Valid @RequestBody UpdateOnboardingProgressRequest request) {
        String tenantIdValue = TenantContext.getTenantId();
        UUID userId = TenantContext.getUserId();

        if (tenantIdValue == null || userId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unauthorized");
        }

        UUID tenantId = UUID.fromString(tenantIdValue);
        return ResponseEntity.ok(userOnboardingService.updateProgress(tenantId, userId, tutorialKey, request));
    }
}
