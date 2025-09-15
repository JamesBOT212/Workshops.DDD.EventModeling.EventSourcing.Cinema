package com.dddheroes.cinema

import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Info
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class OpenApiConfiguration {

    @Bean
    fun cinemaOpenApi(): OpenAPI = OpenAPI()
        .info(
            Info()
                .title("Micro Cinema API")
                .description("REST API for Cinema Management System")
                .version("v1.0.0")
        )

}