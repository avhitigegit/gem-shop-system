package lk.nuvi.gemshop.system.sampledata;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lk.nuvi.gemshop.audit.alert.AlertRepository;
import lk.nuvi.gemshop.audit.alert.AlertType;
import lk.nuvi.gemshop.audit.alert.Alerts;
import lk.nuvi.gemshop.audit.approval.Approval;
import lk.nuvi.gemshop.audit.approval.ApprovalRepository;
import lk.nuvi.gemshop.audit.approval.ApprovalType;
import lk.nuvi.gemshop.audit.log.ActivityLog;
import lk.nuvi.gemshop.auth.user.UserAccount;
import lk.nuvi.gemshop.auth.user.UserAccountRepository;
import lk.nuvi.gemshop.settings.branch.Branch;
import lk.nuvi.gemshop.settings.branch.BranchRepository;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

/**
 * Prototype approvals (4 waiting + history), owner alerts and the activity log.
 * Dates in the texts ("Day closing 21/11/2026", "DC-20261121", licence "renew before …") move with the sample data.
 */
@Component
class AuditSampleData implements SampleDataPart {

    private static final Pattern DMY = Pattern.compile("\\b(\\d{2})/(\\d{2})/(\\d{4})\\b");
    private static final Pattern DC = Pattern.compile("\\bDC-(\\d{8})\\b");
    private static final DateTimeFormatter DMY_F = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter YMD_F = DateTimeFormatter.BASIC_ISO_DATE;
    private static final Map<String, String> PAGES = Map.of(
            "g-payments/invoice.html", "/sell/invoices/",
            "e-returns/credit-note.html", "/sell/credit-notes/",
            "k-owner-control/approvals.html", "/approvals",
            "a-setup/shop-vat.html", "/settings/shop",
            "l-reports/daily-closing.html", "/reports/daily-cash");

    private final ApprovalRepository approvals;
    private final AlertRepository alertRows;
    private final Alerts alerts;
    private final ActivityLog log;
    private final UserAccountRepository users;
    private final BranchRepository branches;

    AuditSampleData(ApprovalRepository approvals, AlertRepository alertRows, Alerts alerts, ActivityLog log,
            UserAccountRepository users, BranchRepository branches) {
        this.approvals = approvals;
        this.alertRows = alertRows;
        this.alerts = alerts;
        this.log = log;
        this.users = users;
        this.branches = branches;
    }

    @Override
    public int order() {
        return 950;
    }

    @Override
    public String name() {
        return "approvals + alerts + activity log";
    }

    @Override
    public int catchUp(SampleData data) {
        return approvals.count() == 0 && alertRows.count() == 0 ? load(data) : 0;
    }

    @Override
    public int load(SampleData data) {
        Map<String, UserAccount> people = people(data);
        Map<String, Branch> shops = new HashMap<>();
        int n = 0;
        long last = 0;
        for (JsonNode a : data.node("approvals").values()) {
            String number = a.path("id").asString();
            Approval ap = new Approval(number, type(a.path("type").asString()), data.instant(a.path("at")),
                    person(people, a.path("requestedBy").asString()), branch(shops, a.path("branch").asString()),
                    a.path("title").asString(), SampleData.text(a.path("detail")), SampleData.decimal(a.path("amount")),
                    ref(a.path("payload")));
            if (!"pending".equals(a.path("status").asString())) {
                ap.decide("approved".equals(a.path("status").asString()), person(people, a.path("decidedBy").asString()),
                        SampleData.text(a.path("note")), data.instant(a.path("decidedAt")));
            }
            approvals.save(ap);
            last = Math.max(last, Long.parseLong(number.substring(number.indexOf('-') + 1)));
            n++;
        }
        if (last > 0) {
            approvals.setNextNumberAfter(last);
        }
        for (JsonNode a : data.node("alerts").values()) {
            alerts.raiseAt(data.instant(a.path("at")), alertType(a.path("type").asString()), shift(a.path("text").asString(), data),
                    link(a.path("link").asString(), a.path("id").asString()), branch(shops, a.path("branch").asString()));
            n++;
        }
        for (JsonNode l : data.node("log").values()) {
            String u = l.path("user").asString();
            log.addAt(data.instant(l.path("at")), "system".equals(u) ? null : person(people, u), l.path("action").asString(),
                    shift(l.path("detail").asString(""), data), branch(shops, l.path("branch").asString()));
            n++;
        }
        return n;
    }

    /** Prototype user id (u1 …) → saved login, found by email (also works when catching up an older database). */
    private Map<String, UserAccount> people(SampleData data) {
        Map<String, UserAccount> m = new HashMap<>();
        for (JsonNode u : data.node("users").values()) {
            users.findByEmail(UserAccount.normalise(u.path("email").asString())).ifPresent(x -> m.put(u.path("id").asString(), x));
        }
        return m;
    }

    private static UserAccount person(Map<String, UserAccount> people, String id) {
        UserAccount u = people.get(id);
        if (u == null) {
            throw new IllegalStateException("Sample data: unknown user '" + id + "'");
        }
        return u;
    }

    private Branch branch(Map<String, Branch> cache, String code) {
        if (code == null || code.isBlank() || "all".equals(code)) {
            return null;
        }
        return cache.computeIfAbsent(code, c -> branches.findByCode(c)
                .orElseThrow(() -> new IllegalStateException("Sample data: unknown branch '" + c + "'")));
    }

    /** The waiting record from the prototype payload: heldId, returnId, saleId, correctionId, expenseId or month. */
    private static String ref(JsonNode payload) {
        for (String k : new String[] {"heldId", "returnId", "saleId", "correctionId", "expenseId", "month"}) {
            String v = SampleData.text(payload.path(k));
            if (v != null) {
                return v;
            }
        }
        return null;
    }

    private static ApprovalType type(String t) {
        return ApprovalType.valueOf(t.toUpperCase(Locale.ROOT));
    }

    private static AlertType alertType(String t) {
        return AlertType.valueOf(t.toUpperCase(Locale.ROOT));
    }

    /** Prototype page → app page, e.g. g-payments/invoice.html?id=INV-000138 → /sell/invoices/INV-000138. */
    static String link(String protoLink, String alertId) {
        if (protoLink == null || protoLink.isBlank()) {
            return null;
        }
        String[] parts = protoLink.split("\\?id=", 2);
        String page = PAGES.get(parts[0]);
        if (page == null) {
            return null;
        }
        if (page.endsWith("/")) {
            return parts.length > 1 ? page + parts[1] : page.substring(0, page.length() - 1);
        }
        if ("/approvals".equals(page) && alertId.startsWith("AL-AP-")) {
            return page + "#" + alertId.substring(3);
        }
        return page;
    }

    /** Moves "21/11/2026" and "DC-20261121" in a text by the same days as the sample dates. */
    static String shift(String text, SampleData data) {
        if (text == null || data.shiftDays() == 0) {
            return text;
        }
        Matcher m = DMY.matcher(text);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            LocalDate d = LocalDate.of(Integer.parseInt(m.group(3)), Integer.parseInt(m.group(2)), Integer.parseInt(m.group(1)));
            m.appendReplacement(sb, d.plusDays(data.shiftDays()).format(DMY_F));
        }
        m.appendTail(sb);
        Matcher dc = DC.matcher(sb.toString());
        StringBuilder out = new StringBuilder();
        while (dc.find()) {
            dc.appendReplacement(out, "DC-" + LocalDate.parse(dc.group(1), YMD_F).plusDays(data.shiftDays()).format(YMD_F));
        }
        dc.appendTail(out);
        return out.toString();
    }
}
