package lk.nuvi.gemshop.common.config;

import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.security.SecurityScheme;
import lk.nuvi.gemshop.common.error.ApiError;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** OpenAPI description at /v3/api-docs and Swagger UI at /swagger-ui.html (not in production). */
@Configuration
public class OpenApiConfig {

    public static final String BEARER = "bearer";

    @Bean
    OpenAPI gemShopOpenApi(AppProperties app) {
        return new OpenAPI()
                .info(new Info()
                        .title(app.name() + " API")
                        .version("v1")
                        .description("Backend API for the Gem Shop System. Errors always use the ApiError format."))
                .components(new Components().addSecuritySchemes(BEARER, new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")));
    }

    /** Every operation documents the one error format, so the frontend gets an ApiError type. */
    @Bean
    OpenApiCustomizer apiErrorResponses() {
        return openApi -> {
            ModelConverters.getInstance().readAll(ApiError.class)
                    .forEach((name, schema) -> openApi.getComponents().addSchemas(name, schema));
            Content content = new Content().addMediaType("application/json",
                    new MediaType().schema(new Schema<>().$ref("#/components/schemas/ApiError")));
            if (openApi.getPaths() == null) {
                return;
            }
            openApi.getPaths().values().forEach(path -> path.readOperations().forEach(op ->
                    op.getResponses().addApiResponse("default",
                            new ApiResponse().description("Error (ApiError format)").content(content))));
        };
    }
}
