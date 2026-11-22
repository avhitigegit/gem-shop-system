package lk.nuvi.gemshop.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Login settings ({@code app.auth.*}).
 *
 * @param jwtSecret          secret that signs the login tokens – at least 32 characters; set JWT_SECRET on servers
 * @param accessMinutes      a login token lasts this long; the app renews it while the person is working
 * @param idleMinutes        signed out after this long without use (shared shop computers)
 * @param sessionHours       longest sign-in without "Keep me signed in"
 * @param rememberDays       "Keep me signed in on this device"
 * @param maxFailedLogins    wrong passwords in a row before the login is locked
 * @param lockMinutes        how long the login stays locked
 * @param resetLinkMinutes   a "forgot password" link works this long, once
 * @param appUrl             address of the app, used in emailed links
 * @param cookieSecure       send the sign-in cookie over HTTPS only (off on your own computer)
 * @param demoLogins         "Demo: sign in as…" buttons on the login page – local and dev only, never UAT or production
 */
@ConfigurationProperties("app.auth")
public record AuthProperties(
        String jwtSecret,
        @DefaultValue("10") int accessMinutes,
        @DefaultValue("15") int idleMinutes,
        @DefaultValue("12") int sessionHours,
        @DefaultValue("30") int rememberDays,
        @DefaultValue("5") int maxFailedLogins,
        @DefaultValue("15") int lockMinutes,
        @DefaultValue("30") int resetLinkMinutes,
        @DefaultValue("http://localhost:8100") String appUrl,
        @DefaultValue("true") boolean cookieSecure,
        @DefaultValue("false") boolean demoLogins) {}
