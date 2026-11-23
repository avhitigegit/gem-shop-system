package lk.nuvi.gemshop.messaging.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lk.nuvi.gemshop.auth.security.Roles;
import lk.nuvi.gemshop.messaging.Birthdays;
import lk.nuvi.gemshop.messaging.Messages;
import lk.nuvi.gemshop.messaging.api.MessageDtos.BirthdayForm;
import lk.nuvi.gemshop.messaging.api.MessageDtos.BirthdayList;
import lk.nuvi.gemshop.messaging.api.MessageDtos.LogList;
import lk.nuvi.gemshop.messaging.api.MessageDtos.MessageSettingsView;
import lk.nuvi.gemshop.messaging.api.MessageDtos.MessageStatus;
import lk.nuvi.gemshop.messaging.api.MessageDtos.SendNowResult;
import lk.nuvi.gemshop.messaging.api.MessageDtos.SesForm;
import lk.nuvi.gemshop.messaging.api.MessageDtos.SmtpForm;
import lk.nuvi.gemshop.messaging.api.MessageDtos.SwitchForm;
import lk.nuvi.gemshop.messaging.api.MessageDtos.TestEmailResult;
import lk.nuvi.gemshop.messaging.api.MessageDtos.VerifyResult;
import lk.nuvi.gemshop.messaging.api.MessageDtos.WhatsappForm;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Email &amp; WhatsApp settings (prototype j-customers-promotions/message-settings.html, Guide 13, 45). Owner only. */
@RestController
@RequestMapping("/api/messages")
@Tag(name = "Messages")
public class MessagesController {

    private final Messages messages;
    private final Birthdays birthdays;

    public MessagesController(Messages messages, Birthdays birthdays) {
        this.messages = messages;
        this.birthdays = birthdays;
    }

    @GetMapping("/status")
    @PreAuthorize(Roles.ANY)
    @Operation(operationId = "messageStatus", summary = "Email on, WhatsApp ready, invoice sent by itself (for the send buttons)")
    public MessageStatus status() {
        return messages.status();
    }

    @GetMapping("/settings")
    @PreAuthorize(Roles.OWNER)
    @Operation(operationId = "messageSettings", summary = "Email & WhatsApp settings (passwords and tokens are never sent back)")
    public MessageSettingsView settings() {
        return messages.view();
    }

    @PutMapping("/smtp")
    @PreAuthorize(Roles.OWNER)
    @Operation(operationId = "saveSmtp", summary = "The shop's own SMTP (empty password keeps the saved one)")
    public MessageSettingsView saveSmtp(@Valid @RequestBody SmtpForm form) {
        return messages.saveSmtp(form);
    }

    @PostMapping("/smtp/test")
    @PreAuthorize(Roles.OWNER)
    @Operation(operationId = "sendTestEmail", summary = "Send a test email to the signed-in owner")
    public TestEmailResult testEmail() {
        return messages.testEmail();
    }

    @PutMapping("/ses")
    @PreAuthorize(Roles.OWNER)
    @Operation(operationId = "saveSes", summary = "Amazon SES for offers (empty secret keeps the saved one)")
    public MessageSettingsView saveSes(@Valid @RequestBody SesForm form) {
        return messages.saveSes(form);
    }

    @PutMapping("/whatsapp")
    @PreAuthorize(Roles.OWNER)
    @Operation(operationId = "verifyWhatsapp", summary = "Save WhatsApp Business details and verify the connection")
    public VerifyResult verifyWhatsapp(@Valid @RequestBody WhatsappForm form) {
        return messages.verifyWhatsapp(form);
    }

    @PutMapping("/switches")
    @PreAuthorize(Roles.OWNER)
    @Operation(operationId = "saveMessageSwitches", summary = "On / off switches: only the ones sent are changed")
    public MessageSettingsView switches(@RequestBody SwitchForm form) {
        return messages.switches(form);
    }

    @PutMapping("/birthday")
    @PreAuthorize(Roles.OWNER)
    @Operation(operationId = "saveBirthdayWishes", summary = "Birthday wishes: channels, time, subject and message ({name}, {shop})")
    public MessageSettingsView saveBirthday(@Valid @RequestBody BirthdayForm form) {
        return messages.saveBirthday(form);
    }

    @GetMapping("/birthdays")
    @PreAuthorize(Roles.OWNER)
    @Operation(operationId = "upcomingBirthdays", summary = "Birthdays in the next 30 days, wishes sent this year, preview")
    public BirthdayList birthdays() {
        return birthdays.list();
    }

    @PostMapping("/birthdays/send-now")
    @PreAuthorize(Roles.OWNER)
    @Operation(operationId = "sendBirthdayWishesNow", summary = "Send today's wishes now (permission only, once a year)")
    public SendNowResult sendNow() {
        return new SendNowResult(birthdays.sendNow());
    }

    @GetMapping("/log")
    @PreAuthorize(Roles.OWNER)
    @Operation(operationId = "messageLog", summary = "The latest messages sent (newest first)")
    public LogList log(@RequestParam(defaultValue = "100") int limit) {
        return new LogList(messages.recent(limit));
    }
}
