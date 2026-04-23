package com.rinoimob.integration;

import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(locations = "classpath:application-test.properties")
public abstract class IntegrationTestBase {

    @Autowired
    protected MockMvc mockMvc;

    protected String baseUrl = "/api/v1";

    protected String getTenantHeader(String tenantId) {
        return tenantId;
    }

    protected String getUserHeader(String userId) {
        return userId;
    }
}
