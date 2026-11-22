package lk.nuvi.gemshop.auth.api;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import lk.nuvi.gemshop.auth.user.Role;

/** What the login screens send and get back. */
public final class AuthDtos {

    private AuthDtos() {}

    public record LoginRequest(
            @NotBlank(message = "Email is required.") @Email(message = "Enter a valid email.") String email,
            @NotBlank(message = "Password is required.") String password,
            @Schema(description = "Keep me signed in on this device (not on shared shop computers)") Boolean remember) {

        public boolean keep() {
            return Boolean.TRUE.equals(remember);
        }
    }

    public record DemoLoginRequest(@NotBlank @Email String email) {}

    public record ChangePasswordRequest(
            @NotBlank(message = "Current password is required.") String currentPassword,
            @NotBlank(message = "New password is required.") String newPassword) {}

    public record ForgotPasswordRequest(@NotBlank(message = "Email is required.") @Email(message = "Enter a valid email.") String email) {}

    public record ResetPasswordRequest(@NotBlank String token, @NotBlank(message = "New password is required.") String newPassword) {}

    public record Message(@Schema(requiredMode = Schema.RequiredMode.REQUIRED) String message) {}

    @Schema(name = "SessionUser")
    public record UserInfo(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long id,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String name,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String email,
            String phone,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Role role,
            @Schema(description = "Branch code; empty for Owner and Accountant (all branches)") String branch) {}

    @Schema(name = "SessionBranch")
    public record BranchInfo(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String code,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String name,
            String shortName,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, allowableValues = {"ACTIVE", "OPENING", "INACTIVE"}) String status) {}

    @Schema(name = "SessionShop")
    public record ShopInfo(@Schema(requiredMode = Schema.RequiredMode.REQUIRED) String name) {}

    @Schema(name = "SessionRates", description = "LKR for 1 USD / 1 EUR")
    public record RatesInfo(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) BigDecimal usd,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) BigDecimal eur,
            Instant updatedAt) {}

    /** The signed-in person and what the app frame needs: shop name, branches, today's rates. */
    public record Me(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UserInfo user,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) ShopInfo shop,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<BranchInfo> branches,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) RatesInfo rates) {}

    /** Answer to sign in / renew: the short login token and who is signed in. The long token is in a cookie. */
    public record Session(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String accessToken,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant expiresAt,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Sign out after this many minutes without use; 0 = kept signed in")
                    int idleMinutes,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Me me) {}

    @Schema(name = "DemoUser")
    public record DemoUser(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String name,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String email,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Role role) {}
}
