package lk.nuvi.gemshop.system.sampledata;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.MonthDay;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;
import tools.jackson.databind.JsonNode;

/**
 * The prototype demo data (prototype-v12.json) moved to "today".
 * <p>The file was exported on {@code exportedOn}; every date is moved forward by the days since then, so the
 * sample data always looks like the prototype: sales in the last 30 days, birthdays this week, licence expiring soon.
 * <p>{@link #ref} keeps prototype ids (e.g. "ella", "u2", "GEM-00101") → saved records, for links between parts.
 */
public final class SampleData {

    private static final DateTimeFormatter MONTH_DAY = DateTimeFormatter.ofPattern("MM-dd");

    private final JsonNode state;
    private final LocalDate exportedOn;
    private final long shiftDays;
    private final Map<String, Object> refs = new HashMap<>();

    public SampleData(JsonNode root, LocalDate today) {
        this.state = root.path("state");
        this.exportedOn = LocalDate.parse(root.path("exportedOn").asString());
        this.shiftDays = ChronoUnit.DAYS.between(exportedOn, today);
    }

    /** A part of the prototype state, e.g. {@code node("branches")}. */
    public JsonNode node(String name) {
        return state.path(name);
    }

    public long shiftDays() {
        return shiftDays;
    }

    /** "2026-11-09" → moved date, or null. */
    public LocalDate date(JsonNode n) {
        String s = text(n);
        return s == null ? null : LocalDate.parse(s.substring(0, 10)).plusDays(shiftDays);
    }

    /** "2026-11-22T03:15:00.000Z" → moved instant, or null. */
    public Instant instant(JsonNode n) {
        String s = text(n);
        return s == null ? null : Instant.parse(s).plus(shiftDays, ChronoUnit.DAYS);
    }

    /** Birthday "MM-DD" (no year) → moved "MM-DD", or null. Keeps "birthday this week" in the sample data. */
    public String birthday(JsonNode n) {
        String s = text(n);
        if (s == null) {
            return null;
        }
        MonthDay md = MonthDay.parse("--" + s);
        int year = md.isValidYear(exportedOn.getYear()) ? exportedOn.getYear() : 2024;
        return md.atYear(year).plusDays(shiftDays).format(MONTH_DAY);
    }

    public static String text(JsonNode n) {
        return n == null || n.isMissingNode() || n.isNull() ? null : n.asString();
    }

    public static BigDecimal decimal(JsonNode n) {
        return n == null || n.isMissingNode() || n.isNull() ? null : n.asDecimal();
    }

    public void ref(String prototypeId, Object saved) {
        refs.put(prototypeId, saved);
    }

    public <T> T ref(String prototypeId, Class<T> type) {
        Object o = refs.get(prototypeId);
        if (o == null) {
            throw new IllegalStateException("Sample data: unknown " + type.getSimpleName() + " '" + prototypeId + "'");
        }
        return type.cast(o);
    }

    public static LocalDate today(ZoneId zone) {
        return LocalDate.now(zone);
    }
}
