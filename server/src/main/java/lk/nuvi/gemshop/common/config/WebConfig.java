package lk.nuvi.gemshop.common.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** CORS for the frontend when it runs on its own port (npm run dev). Behind Nginx it is the same origin. */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final AppProperties app;

    public WebConfig(AppProperties app) {
        this.app = app;
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins(app.cors().allowedOrigins().toArray(String[]::new))
                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                .allowCredentials(true);
    }
}
