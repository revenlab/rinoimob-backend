package com.rinoimob.config.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

class CustomAuthenticationEntryPointTest {

    @Test
    void shouldSerializeErrorResponseWithInstantTimestamp() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        CustomAuthenticationEntryPoint entryPoint = new CustomAuthenticationEntryPoint(objectMapper);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/secure");
        MockHttpServletResponse response = new MockHttpServletResponse();

        entryPoint.commence(request, response, new BadCredentialsException("Missing token"));

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("\"timestamp\"");
        assertThat(response.getContentAsString()).contains("\"path\":\"/api/secure\"");
    }
}
