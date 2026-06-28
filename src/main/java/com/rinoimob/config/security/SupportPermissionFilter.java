package com.rinoimob.config.security;

import com.rinoimob.domain.enums.SystemRole;
import com.rinoimob.domain.repository.SupportUserPermissionRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Component
public class SupportPermissionFilter extends OncePerRequestFilter {

    private static final List<String> SUPPORT_ROLES = List.of(
            "ROLE_" + SystemRole.SUPPORT_MANAGER.name(),
            "ROLE_" + SystemRole.SUPPORT_AGENT.name()
    );

    private final SupportUserPermissionRepository supportUserPermissionRepository;

    public SupportPermissionFilter(SupportUserPermissionRepository supportUserPermissionRepository) {
        this.supportUserPermissionRepository = supportUserPermissionRepository;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated() || !hasSupportRole(authentication.getAuthorities())) {
            filterChain.doFilter(request, response);
            return;
        }

        UUID userId = resolveUserId(request.getAttribute("userId"));
        if (userId == null) {
            filterChain.doFilter(request, response);
            return;
        }

        List<GrantedAuthority> authorities = new ArrayList<>();
        for (GrantedAuthority authority : authentication.getAuthorities()) {
            if (!authority.getAuthority().startsWith("PERMISSION_")) {
                authorities.add(authority);
            }
        }
        supportUserPermissionRepository.findPermissionValuesByUserId(userId).stream()
                .map(permission -> new SimpleGrantedAuthority("PERMISSION_" + permission))
                .forEach(authorities::add);

        UsernamePasswordAuthenticationToken updatedAuthentication =
                new UsernamePasswordAuthenticationToken(authentication.getPrincipal(), authentication.getCredentials(), authorities);
        updatedAuthentication.setDetails(authentication.getDetails());
        SecurityContextHolder.getContext().setAuthentication(updatedAuthentication);

        filterChain.doFilter(request, response);
    }

    private boolean hasSupportRole(Collection<? extends GrantedAuthority> authorities) {
        return authorities.stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(SUPPORT_ROLES::contains);
    }

    private UUID resolveUserId(Object value) {
        if (value instanceof UUID userId) {
            return userId;
        }
        if (value instanceof String userId) {
            try {
                return UUID.fromString(userId);
            } catch (IllegalArgumentException ex) {
                return null;
            }
        }
        return null;
    }
}
