package lk.nuvi.gemshop.notifications.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lk.nuvi.gemshop.auth.security.Roles;
import lk.nuvi.gemshop.notifications.Push;
import lk.nuvi.gemshop.notifications.api.PushDtos.DeviceForm;
import lk.nuvi.gemshop.notifications.api.PushDtos.PushDeviceView;
import lk.nuvi.gemshop.notifications.api.PushDtos.PushKey;
import lk.nuvi.gemshop.notifications.api.PushDtos.PushSettings;
import lk.nuvi.gemshop.notifications.api.PushDtos.PushSettingsForm;
import lk.nuvi.gemshop.notifications.api.PushDtos.PushTestResult;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Push alerts to the owner's phone (P4-02). */
@RestController
@RequestMapping("/api/push")
@Tag(name = "Push alerts")
@PreAuthorize(Roles.OWNER)
public class PushController {

    private final Push push;

    public PushController(Push push) {
        this.push = push;
    }

    @GetMapping("/key")
    @Operation(operationId = "getPushKey", summary = "The VAPID public key a phone signs up with")
    public PushKey key() {
        return new PushKey(push.publicKey());
    }

    @PostMapping("/devices")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(operationId = "addPushDevice", summary = "This phone / browser wants push alerts")
    public PushDeviceView add(@Valid @RequestBody DeviceForm form) {
        return push.addDevice(form);
    }

    @DeleteMapping("/devices")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(operationId = "removePushDevice", summary = "Stop push alerts to this phone / browser")
    public void remove(@RequestParam String endpoint) {
        push.removeDevice(endpoint);
    }

    @GetMapping("/settings")
    @Operation(operationId = "getPushSettings", summary = "Which pushes are on, and this person's phones")
    public PushSettings settings() {
        return push.settings();
    }

    @PutMapping("/settings")
    @Operation(operationId = "savePushSettings", summary = "Turn push kinds on or off")
    public PushSettings save(@Valid @RequestBody PushSettingsForm form) {
        return push.saveSettings(form.kinds());
    }

    @PostMapping("/test")
    @Operation(operationId = "sendTestPush", summary = "Send a test push to my phones")
    public PushTestResult test() {
        return new PushTestResult(push.test());
    }
}
