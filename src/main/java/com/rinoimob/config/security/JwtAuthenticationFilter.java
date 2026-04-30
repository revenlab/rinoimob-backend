package com.rinoimob.config.security;

import com.rinoimob.context.TenantContext;
import com.rinoimob.service.auth.TokenService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Slf4j
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider tokenProvider;
    private final TokenService tokenService;

    public JwtAuthenticationFilter(JwtTokenProvider tokenProvider, TokenService tokenService) {
        this.tokenProvider = tokenProvider;
        this.tokenService = tokenService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        try {
            String jwt = getJwtFromRequest(request);

            if (StringUtils.hasText(jwt) && tokenProvider.isAccessToken(jwt) && tokenProvider.isTokenValid(jwt)) {
                String jti = tokenProvider.getJtiFromToken(jwt);

                if (jti != null && !tokenService.isValid(jti)) {
                    filterChain.doFilter(request, response);
                    return;
                }

                var userId = tokenProvider.getUserIdFromToken(jwt);
                var email = tokenProvider.getEmailFromToken(jwt);
                var role = tokenProvider.getRoleFromToken(jwt);
                var tenantId = tokenProvider.getTenantIdFromToken(jwt);
                var permissions = tokenProvider.getPermissionsFromToken(jwt);

                List<SimpleGrantedAuthority> authorities = new ArrayList<>();
                if (role != null) {
                    authorities.add(new SimpleGrantedAuthority("ROLE_" + role));
                }
                for (String permission : permissions) {
                    authorities.add(new SimpleGrantedAuthority("PERMISSION_" + permission));
                }

                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(email, null, authorities);
                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authentication);

                request.setAttribute("userId", userId);
                request.setAttribute("email", email);
                request.setAttribute("role", role);
                if (jti != null) {
                    request.setAttribute("jti", jti);
                }

                if (tenantId != null) {
                    TenantContext.setTenantId(tenantId.toString());
                }
            }
        } catch (Exception ex) {
            log.error("Could not set user authentication in security context", ex);
        }

        filterChain.doFilter(request, response);
    }

    private String getJwtFromRequest(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }
}
