package lk.nuvi.gemshop.system.setup;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import lk.nuvi.gemshop.auth.api.AuthController;
import lk.nuvi.gemshop.auth.api.AuthDtos.Session;
import lk.nuvi.gemshop.auth.security.PublicApi;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Owner first-time setup (prototype auth/signup.html): only works while the system has no logins. */
@RestController
@RequestMapping("/api/setup")
@Tag(name = "First-time setup")
public class SetupController {

    private final SetupService setup;
    private final AuthController auth;

    public SetupController(SetupService setup, AuthController auth) {
        this.setup = setup;
        this.auth = auth;
    }

    public record SetupStatus(@Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "true = no logins yet, setup is needed") boolean needed) {}

    public record OwnerPart(
            @NotBlank(message = "Full name is required.") @Size(max = 120) String name,
            @NotBlank(message = "Email is required.") @Email(message = "Enter a valid email.") String email,
            @NotBlank(message = "Mobile is required.") @Size(max = 40) String phone,
            @NotBlank(message = "Password is required.") String password) {}

    public record ShopPart(
            @NotBlank(message = "Shop name is required.") @Size(max = 120) String name,
            @NotBlank(message = "Address is required.") @Size(max = 255) String address,
            @NotBlank(message = "NGJA licence no. is required.") @Size(max = 60) String ngjaLicenceNo,
            @NotNull(message = "NGJA licence expiry is required.") LocalDate ngjaLicenceExpiry) {}

    public record BranchPart(@NotBlank(message = "First branch is required.") @Size(max = 120) String name) {}

    public record RatesPart(
            @NotNull(message = "USD rate is required.") @DecimalMin(value = "0.0001", message = "Must be more than 0.") BigDecimal usd,
            @NotNull(message = "EUR rate is required.") @DecimalMin(value = "0.0001", message = "Must be more than 0.") BigDecimal eur) {}

    public record SetupRequest(
            @NotNull @Valid OwnerPart owner,
            @NotNull @Valid ShopPart shop,
            @NotNull @Valid BranchPart branch,
            @NotNull @Valid RatesPart rates,
            @AssertTrue(message = "Please agree to keep customer data private.") boolean terms) {}

    @PublicApi
    @GetMapping("/status")
    @Operation(operationId = "getSetupStatus", summary = "Is the owner first-time setup still needed?")
    public SetupStatus status() {
        return new SetupStatus(setup.needed());
    }

    @PublicApi
    @PostMapping
    @Operation(operationId = "runSetup", summary = "Create the owner, shop, first branch and rates; signs the owner in")
    public Session run(@Valid @RequestBody SetupRequest body, HttpServletRequest req, HttpServletResponse res) {
        return auth.send(setup.run(body, req.getHeader(HttpHeaders.USER_AGENT)), res);
    }
}
