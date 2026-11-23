package lk.nuvi.gemshop.messaging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Sends a WhatsApp message with a PDF (invoice, quotation). Until the shop's WhatsApp Business account is connected
 * (P6-03) this is the <b>test mode</b>: nothing leaves the system, the message is written to the server log so it can be
 * checked, and the screen says "test mode".
 */
@Service
public class WhatsApp {

    private static final Logger log = LoggerFactory.getLogger(WhatsApp.class);

    /** True while no real WhatsApp account is connected. */
    public boolean testMode() {
        return true;
    }

    /** Returns true when the message was accepted (in test mode: always, after writing it to the log). */
    public boolean send(String to, String text, String fileName, byte[] file) {
        return send(to, text, fileName, file, null);
    }

    /**
     * @param callback sent to Meta as {@code biz_opaque_callback_data}; Meta returns it with the "delivered" and "read"
     *     statuses (offer results, P4-05)
     */
    public boolean send(String to, String text, String fileName, byte[] file, String callback) {
        log.info("WhatsApp (test mode) to {}: {} [attachment {} – {} bytes]", to, text.replace('\n', ' '), fileName,
                file == null ? 0 : file.length);
        return true;
    }
}
