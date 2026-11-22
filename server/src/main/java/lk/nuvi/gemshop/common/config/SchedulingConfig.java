package lk.nuvi.gemshop.common.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Night jobs (e.g. removing uploads never saved with a record). Times are Sri Lanka time. */
@Configuration
@EnableScheduling
public class SchedulingConfig {}
