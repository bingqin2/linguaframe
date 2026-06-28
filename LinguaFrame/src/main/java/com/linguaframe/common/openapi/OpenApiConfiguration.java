package com.linguaframe.common.openapi;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfiguration {

    @Bean
    OpenAPI linguaFrameOpenApi() {
        return new OpenAPI()
                .components(new Components()
                        .addSecuritySchemes(
                                "DemoAccessToken",
                                new SecurityScheme()
                                        .type(SecurityScheme.Type.APIKEY)
                                        .in(SecurityScheme.In.HEADER)
                                        .name("X-LinguaFrame-Demo-Token")
                                        .description("Private demo access header. Required only when linguaframe.demo.access-token is configured.")
                        )
                        .addSecuritySchemes(
                                "BearerAuth",
                                new SecurityScheme()
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")
                                        .description("Local account bearer token from /api/auth/login.")
                        ))
                .info(new Info()
                        .title("LinguaFrame API")
                        .version("0.0.1")
                        .description("API documentation for the LinguaFrame video localization backend."));
    }
}
