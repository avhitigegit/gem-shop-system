package lk.nuvi.gemshop.common.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** Settings under {@code app.*} in application.yml. */
@ConfigurationProperties("app")
public record AppProperties(
        @DefaultValue("Gem Shop System") String name,
        @DefaultValue("local") String environment,
        @DefaultValue("Asia/Colombo") String timezone,
        @DefaultValue Cors cors,
        @DefaultValue SampleData sampleData,
        @DefaultValue Crypto crypto,
        @DefaultValue Files files,
        @DefaultValue Push push,
        @DefaultValue Whatsapp whatsapp) {

    public record Cors(@DefaultValue("http://localhost:3000") List<String> allowedOrigins) {}

    /** Prototype demo data on an empty database: local, dev and uat only – never prod. */
    public record SampleData(@DefaultValue("false") boolean enabled, String password) {}

    /**
     * Encryption of passport, phone and email (P1-05).
     *
     * @param key at least 32 random characters (ENCRYPTION_KEY). Keep a safe copy: without it the encrypted data
     *            cannot be read again, also not from a backup.
     */
    public record Crypto(String key) {}

    /**
     * Uploaded files (P1-05).
     *
     * @param dir         folder for the files (Docker volume on the server; nightly copy to S3)
     * @param linkMinutes how long a download link works
     * @param keepUnattachedHours uploads never attached to a record are removed after this
     */
    /**
     * Web Push to the owner's phone (P4-02).
     *
     * @param subject      contact for the push services ("mailto:…" or "https://…"); empty = the shop's e-mail
     * @param allowedHosts push services the server may send to (host or ".suffix")
     * @param allowHttp    tests only: allow a plain-http push endpoint
     */
    public record Push(String subject,
            @DefaultValue({"fcm.googleapis.com", ".push.apple.com", ".push.services.mozilla.com", ".notify.windows.com"})
            List<String> allowedHosts,
            @DefaultValue("false") boolean allowHttp) {}

    /**
     * Messages coming in from WhatsApp (P4-03: a customer replies STOP). Meta calls the webhook
     * {@code /api/public/whatsapp/webhook}; real numbers are connected in P6-03.
     *
     * @param verifyToken the word typed in the Meta app when the webhook is set up (empty = webhook off)
     * @param appSecret   the Meta app secret: every call is signed with it (X-Hub-Signature-256); empty = webhook off
     */
    public record Whatsapp(String verifyToken, String appSecret) {}

    public record Files(@DefaultValue("./data/files") String dir, @DefaultValue("60") int linkMinutes,
            @DefaultValue("24") int keepUnattachedHours) {}
}
