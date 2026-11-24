package lk.nuvi.gemshop.customers;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import lk.nuvi.gemshop.common.crypto.FieldCrypto;
import org.flywaydb.core.api.callback.Callback;
import org.flywaydb.core.api.callback.Context;
import org.flywaydb.core.api.callback.Event;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Right after the database migrations: encrypts any customer email / WhatsApp number still stored as plain text (rows
 * from before V22, P6-01). Runs on every start but only changes rows that are not encrypted yet ({@code v1:…}), so it is
 * safe to run again. It needs the app's ENCRYPTION_KEY, which is why it is Java and not SQL.
 */
@Component
public class EncryptCustomerContacts implements Callback {

    private static final Logger LOG = LoggerFactory.getLogger(EncryptCustomerContacts.class);
    private static final String PLAIN = "(%1$s is not null and %1$s not like 'v1:%%')";

    private final FieldCrypto crypto;

    public EncryptCustomerContacts(FieldCrypto crypto) {
        this.crypto = crypto;
    }

    @Override
    public boolean supports(Event event, Context context) {
        return event == Event.AFTER_MIGRATE;
    }

    @Override
    public boolean canHandleInTransaction(Event event, Context context) {
        return true;
    }

    @Override
    public void handle(Event event, Context context) {
        Connection db = context.getConnection();
        try {
            if (!hasTable(db)) {
                return;
            }
            int n = 0;
            String select = "select id, email, whatsapp from customer where " + PLAIN.formatted("email") + " or " + PLAIN.formatted("whatsapp");
            try (PreparedStatement q = db.prepareStatement(select); ResultSet rs = q.executeQuery();
                    PreparedStatement u = db.prepareStatement("update customer set email = ?, whatsapp = ? where id = ?")) {
                while (rs.next()) {
                    u.setString(1, encrypt(rs.getString(2)));
                    u.setString(2, encrypt(rs.getString(3)));
                    u.setLong(3, rs.getLong(1));
                    u.addBatch();
                    n++;
                }
                if (n > 0) {
                    u.executeBatch();
                }
            }
            if (n > 0) {
                LOG.info("Encrypted the email / WhatsApp of {} customer(s)", n);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Could not encrypt customer contacts", e);
        }
    }

    private String encrypt(String value) {
        return value == null || value.startsWith("v1:") ? value : crypto.encrypt(value);
    }

    private static boolean hasTable(Connection db) throws SQLException {
        try (ResultSet rs = db.getMetaData().getColumns(null, null, "customer", "whatsapp")) {
            return rs.next();
        }
    }

    @Override
    public String getCallbackName() {
        return "encrypt-customer-contacts";
    }
}
