package com.rinoimob.api.controller;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

import jakarta.servlet.http.HttpServletRequest;
import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

class UserControllerSecurityTest {

    @Test
    void listUsersRequiresUsersReadPermission() throws Exception {
        Method method = UserController.class.getMethod("listUsers", HttpServletRequest.class);

        PreAuthorize preAuthorize = method.getAnnotation(PreAuthorize.class);

        assertThat(preAuthorize).isNotNull();
        assertThat(preAuthorize.value()).isEqualTo("hasAuthority('PERMISSION_users:read')");
    }
}
