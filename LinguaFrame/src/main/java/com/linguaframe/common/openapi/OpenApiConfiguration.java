package com.linguaframe.common.openapi;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfiguration {

    @Bean
    OpenAPI linguaFrameOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("LinguaFrame API")
                        .version("0.0.1")
                        .description("API documentation for the LinguaFrame video localization backend."));
    }
}
