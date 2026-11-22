package lk.nuvi.gemshop.common.persistence;

import java.util.Optional;
import lk.nuvi.gemshop.auth.security.CurrentUser;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/** Fills created/updated at and by on every save. */
@Configuration
@EnableJpaAuditing(auditorAwareRef = "auditor")
public class AuditingConfig {

    public static final String SYSTEM = "system";

    /** Who is saving: the signed-in person's email, or {@code system} for automatic jobs, sign-in and the sample data. */
    @Bean
    AuditorAware<String> auditor() {
        return () -> Optional.of(CurrentUser.get().map(CurrentUser.Who::email).filter(e -> e != null && !e.isBlank())
                .orElse(SYSTEM));
    }
}
