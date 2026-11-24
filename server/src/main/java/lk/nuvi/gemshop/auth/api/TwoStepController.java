package lk.nuvi.gemshop.auth.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.Instant;
import java.util.Base64;
import lk.nuvi.gemshop.auth.TwoStep;
import lk.nuvi.gemshop.auth.api.AuthDtos.TwoStepBackupCodes;
import lk.nuvi.gemshop.auth.api.AuthDtos.TwoStepCodeRequest;
import lk.nuvi.gemshop.auth.api.AuthDtos.TwoStepOffRequest;
import lk.nuvi.gemshop.auth.api.AuthDtos.TwoStepSetup;
import lk.nuvi.gemshop.auth.api.AuthDtos.TwoStepStatus;
import lk.nuvi.gemshop.auth.security.CurrentUser;
import lk.nuvi.gemshop.auth.security.Roles;
import lk.nuvi.gemshop.files.codes.Codes;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Two-step sign-in for the owner (P6-01): set up with an authenticator app, backup codes, switch off. */
@RestController
@RequestMapping("/api/auth/two-step")
@Tag(name = "Login")
public class TwoStepController {

    private final TwoStep twoStep;
    private final CurrentUser current;
    private final Codes codes;

    public TwoStepController(TwoStep twoStep, CurrentUser current, Codes codes) {
        this.twoStep = twoStep;
        this.current = current;
        this.codes = codes;
    }

    @PreAuthorize(Roles.ANY)
    @GetMapping
    @Operation(operationId = "getTwoStep", summary = "Is two-step sign-in on for my login?")
    public TwoStepStatus status() {
        TwoStep.Status s = twoStep.status(current.require().id());
        return new TwoStepStatus(s.on(), s.since(), s.backupCodesLeft(), s.allowed());
    }

    @PreAuthorize(Roles.OWNER)
    @PostMapping("/start")
    @Operation(operationId = "startTwoStep", summary = "New secret for the authenticator app (QR code); stays off until confirmed")
    public TwoStepSetup start() {
        TwoStep.Setup s = twoStep.start(current.require().id());
        // the QR picture comes with the answer, so the secret is never in a web address (server logs)
        String qr = "data:image/png;base64," + Base64.getEncoder().encodeToString(codes.qr(s.uri(), 220));
        return new TwoStepSetup(s.secret(), s.uri(), qr);
    }

    @PreAuthorize(Roles.OWNER)
    @PostMapping("/confirm")
    @Operation(operationId = "confirmTwoStep", summary = "Turn two-step on with the first code; returns the backup codes (shown once)")
    public TwoStepBackupCodes confirm(@Valid @RequestBody TwoStepCodeRequest body) {
        return new TwoStepBackupCodes(twoStep.confirm(current.require().id(), body.code(), Instant.now()));
    }

    @PreAuthorize(Roles.OWNER)
    @PostMapping("/off")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(operationId = "turnOffTwoStep", summary = "Switch two-step sign-in off (password needed)")
    public void off(@Valid @RequestBody TwoStepOffRequest body) {
        twoStep.off(current.require().id(), body.password());
    }
}
