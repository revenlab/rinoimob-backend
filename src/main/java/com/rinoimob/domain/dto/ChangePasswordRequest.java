package com.rinoimob.domain.dto;

public record ChangePasswordRequest(String currentPassword, String newPassword, String confirmPassword) {}
