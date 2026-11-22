package lk.nuvi.gemshop.auth.security;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import lk.nuvi.gemshop.auth.config.AuthProperties;
import lk.nuvi.gemshop.auth.user.UserAccount;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

/** Makes the short login token (JWT): who you are, your role and your branch. */
@Service
public class TokenService {

    public static final String ROLE = "role";
    public static final String BRANCH = "branch";
    public static final String EMAIL = "email";
    public static final String NAME = "name";

    private final JwtEncoder encoder;
    private final AuthProperties props;

    public TokenService(JwtEncoder encoder, AuthProperties props) {
        this.encoder = encoder;
        this.props = props;
    }

    public record AccessToken(String token, Instant expiresAt) {}

    public AccessToken issue(UserAccount user) {
        Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        Instant exp = now.plus(props.accessMinutes(), ChronoUnit.MINUTES);
        JwtClaimsSet.Builder claims = JwtClaimsSet.builder()
                .issuer("gemshop")
                .subject(String.valueOf(user.getId()))
                .issuedAt(now)
                .expiresAt(exp)
                .claim(ROLE, user.getRole().name())
                .claim(EMAIL, user.getEmail())
                .claim(NAME, user.getName());
        if (user.getBranch() != null) {
            claims.claim(BRANCH, user.getBranch().getId());
        }
        String token = encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims.build()))
                .getTokenValue();
        return new AccessToken(token, exp);
    }
}
