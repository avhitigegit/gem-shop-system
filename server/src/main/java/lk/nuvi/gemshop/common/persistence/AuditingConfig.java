package lk.nuvi.gemshop.common.persistence;

import java.util.Optional;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/** Fills created/updated at and by on every save. */
@Configuration
@EnableJpaAuditing(auditorAwareRef = "auditor")
public class AuditingConfig {

    public static final String SYSTEM = "system";

    /**
     * Who is saving. Until login exists (P1-02) everything is saved as {@code system};
     * P1-02 replaces this with the logged-in user's email.
     */
    @Bean
    AuditorAware<String> auditor() {
        return () -> Optional.of(SYSTEM);
    }
}
