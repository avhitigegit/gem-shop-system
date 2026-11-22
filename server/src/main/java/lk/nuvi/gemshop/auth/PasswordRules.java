package lk.nuvi.gemshop.auth;

import lk.nuvi.gemshop.common.error.ApiException;
import org.springframework.http.HttpStatus;

/** Passwords: at least 8 characters, including a number (same as the prototype). */
public final class PasswordRules {

    public static final String WEAK_PASSWORD = "WEAK_PASSWORD";
    public static final String HINT = "At least 8 characters, including a number.";

    private PasswordRules() {}

    public static boolean ok(String pw) {
        return pw != null && pw.length() >= 8 && pw.length() <= 72 && pw.chars().anyMatch(Character::isDigit);
    }

    /** Stops with "Password too weak" and marks the field red. */
    public static void check(String pw, String field) {
        if (!ok(pw)) {
            throw ApiException.field(HttpStatus.BAD_REQUEST, WEAK_PASSWORD, "Password too weak. " + HINT, field, HINT);
        }
    }
}
