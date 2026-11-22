package lk.nuvi.gemshop.common.numbers;

import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Running numbers without gaps: {@code numbers.next(Counter.INVOICE)} → "INV-000141".
 * <p>Must be called inside the transaction that saves the document. The counter row stays locked until that
 * transaction ends: two sales at the same moment get different numbers, and if the sale fails the number is
 * given back, so there is never a gap (the accountant and tax office can see every invoice).
 */
@Service
public class Numbers {

    private final JdbcTemplate jdbc;

    public Numbers(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public String next(Counter c) {
        Map<String, Object> row = jdbc.queryForMap("""
                update number_counter
                set last_value = last_value + 1, updated_at = now(), version = version + 1
                where name = ?
                returning prefix, digits, last_value""", c.name());
        return format((String) row.get("prefix"), ((Number) row.get("digits")).intValue(), ((Number) row.get("last_value")).longValue());
    }

    /** The number the next document will get (for "New purchase · PUR-0015" on a form). Not reserved. */
    @Transactional(readOnly = true)
    public String peek(Counter c) {
        Map<String, Object> row = jdbc.queryForMap("select prefix, digits, last_value from number_counter where name = ?", c.name());
        return format((String) row.get("prefix"), ((Number) row.get("digits")).intValue(), ((Number) row.get("last_value")).longValue() + 1);
    }

    /** Sample data only: continue after the prototype's numbers. Never lowers a counter. */
    @Transactional
    public void continueAfter(Counter c, long lastUsed) {
        jdbc.update("update number_counter set last_value = greatest(last_value, ?), updated_at = now() where name = ?", lastUsed, c.name());
    }

    @Transactional(readOnly = true)
    public long lastUsed(Counter c) {
        Long v = jdbc.queryForObject("select last_value from number_counter where name = ?", Long.class, c.name());
        return v == null ? 0 : v;
    }

    static String format(String prefix, int digits, long value) {
        String n = Long.toString(value);
        return prefix + (n.length() >= digits ? n : "0".repeat(digits - n.length()) + n);
    }
}
