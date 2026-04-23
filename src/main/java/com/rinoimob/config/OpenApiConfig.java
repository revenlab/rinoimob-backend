package com.rinoimob.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Rinoimob API")
                        .version("1.0.0")
                        .description("Property Management SaaS Platform API")
                        .contact(new Contact()
                                .name("Rinoimob Team")
                                .url("https://rinoimob.com")
                                .email("api@rinoimob.com"))
                        .license(new License()
                                .name("Apache 2.0")
                                .url("https://www.apache.org/licenses/LICENSE-2.0.html")))
                .addServersItem(new Server()
                        .url("http://localhost:39000")
                        .description("Development Server"))
                .addServersItem(new Server()
                        .url("https://api.rinoimob.com")
                        .description("Production Server"));
    }
}
