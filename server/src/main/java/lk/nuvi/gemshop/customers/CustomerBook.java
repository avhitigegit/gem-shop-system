package lk.nuvi.gemshop.customers;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.MonthDay;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import lk.nuvi.gemshop.audit.log.ActivityLog;
import lk.nuvi.gemshop.auth.config.AuthProperties;
import lk.nuvi.gemshop.auth.security.CurrentUser;
import lk.nuvi.gemshop.auth.user.Role;
import lk.nuvi.gemshop.common.config.AppProperties;
import lk.nuvi.gemshop.common.crypto.Masks;
import lk.nuvi.gemshop.common.error.ApiException;
import lk.nuvi.gemshop.common.error.ErrorCode;
import lk.nuvi.gemshop.customers.api.CustomerDtos.CustomerDetailsForm;
import lk.nuvi.gemshop.customers.api.CustomerDtos.CustomerList;
import lk.nuvi.gemshop.customers.api.CustomerDtos.CustomerProfile;
import lk.nuvi.gemshop.customers.api.CustomerDtos.CustomerRow;
import lk.nuvi.gemshop.customers.api.CustomerDtos.CustomerGem;
import lk.nuvi.gemshop.customers.api.CustomerDtos.CustomerMessage;
import lk.nuvi.gemshop.customers.api.CustomerDtos.CustomerPurchase;
import lk.nuvi.gemshop.customers.api.CustomerDtos.CustomerQuote;
import lk.nuvi.gemshop.customers.api.CustomerDtos.UnsubscribeView;
import lk.nuvi.gemshop.sales.Quote;
import lk.nuvi.gemshop.sales.QuoteRepository;
import lk.nuvi.gemshop.sales.Sale;
import lk.nuvi.gemshop.sales.SaleRepository;
import lk.nuvi.gemshop.settings.shop.ShopSettings;
import lk.nuvi.gemshop.settings.shop.ShopSettingsRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The customers screens (P4-03, BRD J1, J2, J8, F5): list with birthdays, add, edit, one customer with purchases, quotes and
 * messages, the permission switch, the unsubscribe link and WhatsApp STOP. Owner and salesperson; the passport (encrypted) is
 * shown masked to the owner only. Purchases count the branch at the top.
 */
@Service
public class CustomerBook {

    private static final java.util.regex.Pattern PASSPORT = java.util.regex.Pattern.compile("^[A-Z0-9]{4,20}$");

    private final CustomerRepository customers;
    private final Customers rules;
    private final SaleRepository sales;
    private final QuoteRepository quotes;
    private final ShopSettingsRepository shops;
    private final ActivityLog log;
    private final CurrentUser current;
    private final ZoneId zone;
    private final String appUrl;
    private final org.springframework.context.ApplicationEventPublisher events;

    /** A customer unsubscribed (link or WhatsApp STOP): offer results count it (P4-05). */
    public record Unsubscribed(long customerId, Instant at) {}
    private final org.springframework.beans.factory.ObjectProvider<lk.nuvi.gemshop.messaging.Messages> messageLog;
    private final org.springframework.beans.factory.ObjectProvider<lk.nuvi.gemshop.messaging.Birthdays> wishes;

    public CustomerBook(CustomerRepository customers, Customers rules, SaleRepository sales, QuoteRepository quotes,
            ShopSettingsRepository shops, ActivityLog log, CurrentUser current, AppProperties app, AuthProperties auth,
            org.springframework.beans.factory.ObjectProvider<lk.nuvi.gemshop.messaging.Messages> messageLog,
            org.springframework.beans.factory.ObjectProvider<lk.nuvi.gemshop.messaging.Birthdays> wishes,
            org.springframework.context.ApplicationEventPublisher events) {
        this.customers = customers;
        this.rules = rules;
        this.sales = sales;
        this.quotes = quotes;
        this.shops = shops;
        this.log = log;
        this.current = current;
        this.zone = ZoneId.of(app.timezone());
        this.appUrl = auth.appUrl().replaceAll("/+$", "");
        this.messageLog = messageLog;
        this.wishes = wishes;
        this.events = events;
    }

    // ------------------------------------------------------------------ list and profile

    @Transactional(readOnly = true)
    public CustomerList list() {
        Map<Long, Object[]> spent = spent();
        LocalDate today = LocalDate.now(zone);
        return new CustomerList(customers.findAllByOrderByNameAsc().stream().map(c -> row(c, spent.get(c.getId()), today)).toList());
    }

    @Transactional(readOnly = true)
    public CustomerProfile profile(String code) {
        Customer c = require(code);
        Long branch = current.scope().branchId();
        List<Sale> ss = sales.ofCustomer(c.getId(), branch);
        List<Quote> qs = quotes.ofCustomer(c.getId(), branch);
        List<Sale> live = ss.stream().filter(s -> s.getStatus() == Sale.Status.COMPLETED).toList();
        Object[] totals = {c.getId(), (long) live.size(), live.stream().map(Sale::getTotal).reduce(BigDecimal.ZERO, BigDecimal::add)};
        boolean owner = current.require().role() == Role.OWNER;
        List<CustomerPurchase> purchases = ss.stream().map(s -> new CustomerPurchase(s.getNumber(), s.getSoldAt(), s.getBranch().getName(),
                s.getItems().stream().map(i -> new CustomerGem(i.getGem().getCode(), i.getGem().getName(), i.getGem().getGemType(),
                        i.getGem().getProductType())).toList(),
                s.getTotal(), s.getStatus().name())).toList();
        List<CustomerQuote> qrows = qs.stream().map(q -> new CustomerQuote(q.getNumber(), q.getKind().name(), q.getCreatedOn(),
                q.getItems().stream().map(i -> i.getGem().getCode()).toList(), q.getExpires(), q.getStatus().name())).toList();
        List<CustomerMessage> messages = new ArrayList<>();
        for (Sale s : ss) {
            if (s.isSentEmail() || s.isSentWhatsapp()) {
                messages.add(new CustomerMessage(s.getSoldAt(), "INVOICE", "Invoice " + s.getNumber(), s.isSentEmail(), s.isSentWhatsapp()));
            }
        }
        for (Quote q : qs) {
            if (q.isSentEmail() || q.isSentWhatsapp()) {
                messages.add(new CustomerMessage(q.getCreatedOn(), q.isReservation() ? "RESERVATION" : "QUOTATION",
                        (q.isReservation() ? "Reservation " : "Quotation ") + q.getNumber(), q.isSentEmail(), q.isSentWhatsapp()));
            }
        }
        for (Object[] r : messageLog.getObject().customerMessages(c.getId())) {   // thank-you messages (P4-04)
            String what = "OFFER".equals(r[1]) ? "Offer " : "Thank-you message · ";
            messages.add(new CustomerMessage((Instant) r[0], (String) r[1], what + r[2], (Boolean) r[3], (Boolean) r[4]));
        }
        for (Object[] r : wishes.getObject().ofCustomer(c.getId())) {              // birthday wishes (P4-04)
            messages.add(new CustomerMessage((Instant) r[0], "BIRTHDAY", "Birthday wish", "sent".equals(r[1]), "sent".equals(r[2])));
        }
        messages.sort(Comparator.comparing(CustomerMessage::at).reversed());
        boolean hasPassport = c.getPassport() != null && !c.getPassport().isBlank();
        return new CustomerProfile(row(c, totals, LocalDate.now(zone)), owner ? Masks.passport(c.getPassport()) : null, hasPassport,
                c.getUnsubVia() == null ? null : c.getUnsubVia().name(), c.getNotes(), purchases, qrows, messages);
    }

    // ------------------------------------------------------------------ add / edit / permission

    @Transactional
    public CustomerRow create(CustomerDetailsForm f) {
        Customer c = rules.add(details(f, Boolean.TRUE.equals(f.consent())), Instant.now());
        String p = passport(f.passport());
        if (p != null) {
            c.passport(p);
        }
        return row(c, null, LocalDate.now(zone));
    }

    @Transactional
    public CustomerRow update(String code, CustomerDetailsForm f) {
        Customer c = require(code);
        Customer.Details d = details(f, c.isConsent());
        List<String> changed = new ArrayList<>();
        diff(changed, "name", c.getName(), d.name());
        diff(changed, "country", c.getCountry(), d.country());
        diff(changed, "email", c.getEmail(), d.email());
        diff(changed, "WhatsApp", c.getWhatsapp(), d.whatsapp());
        diff(changed, "birthday", c.getBirthday(), d.birthday());
        c.change(d);
        String p = passport(f.passport());
        if (p != null && !p.equals(c.getPassport())) {
            c.passport(p);
            changed.add("passport");
        }
        if (!changed.isEmpty()) {
            log.add("Customer edited", c.getCode() + " · " + c.getName() + " · " + String.join(", ", changed), null);
        }
        return row(c, spent().get(c.getId()), LocalDate.now(zone));
    }

    /** The permission switch on the customer's page (given at the counter, or withdrawn). */
    @Transactional
    public CustomerRow consent(String code, boolean on) {
        Customer c = require(code);
        if (on != c.canMessage()) {
            boolean wasUnsubscribed = c.isUnsubscribed();
            c.consent(on, Instant.now());
            log.add("Consent changed", c.getCode() + " · " + c.getName() + ": " + (on ? "given" : "withdrawn")
                    + (on && wasUnsubscribed ? " again after unsubscribing" : ""), null);
        }
        return row(c, spent().get(c.getId()), LocalDate.now(zone));
    }

    // ------------------------------------------------------------------ unsubscribe link and WhatsApp STOP

    /** The link put in every offer (P4-05): the customer opens it to stop offers and birthday wishes. */
    public String unsubscribeLink(Customer c) {
        return appUrl + "/unsubscribe/" + c.getUnsubToken();
    }

    @Transactional(readOnly = true)
    public UnsubscribeView unsubscribePage(String token) {
        Customer c = byToken(token);
        return new UnsubscribeView(firstName(c.getName()), shopName(), c.isUnsubscribed());
    }

    @Transactional
    public UnsubscribeView unsubscribe(String token) {
        Customer c = byToken(token);
        if (c.unsubscribe(Instant.now(), Customer.UnsubVia.LINK)) {
            log.addSystem("Unsubscribed", c.getCode() + " · " + c.getName() + " clicked the unsubscribe link", null);
            events.publishEvent(new Unsubscribed(c.getId(), c.getUnsubscribedAt()));
        }
        return new UnsubscribeView(firstName(c.getName()), shopName(), true);
    }

    /**
     * A WhatsApp message came in. "STOP" (also "UNSUBSCRIBE", "STOP ALL") unsubscribes every customer with that number.
     *
     * @param from the sender's number as WhatsApp gives it (country code + number, digits only)
     * @return how many customers were unsubscribed
     */
    @Transactional
    public int whatsappMessage(String from, String text) {
        if (!isStop(text)) {
            return 0;
        }
        String number = digits(from);
        if (number.length() < 6) {
            return 0;
        }
        int n = 0;
        for (Customer c : customers.findByWhatsappIsNotNull()) {
            if (number.equals(digits(c.getWhatsapp())) && c.unsubscribe(Instant.now(), Customer.UnsubVia.WHATSAPP)) {
                log.addSystem("Unsubscribed", c.getCode() + " · " + c.getName() + " sent STOP on WhatsApp", null);
                events.publishEvent(new Unsubscribed(c.getId(), c.getUnsubscribedAt()));
                n++;
            }
        }
        return n;
    }

    static boolean isStop(String text) {
        if (text == null) {
            return false;
        }
        String t = text.trim().replaceAll("[\\s.!]+$", "").replaceAll("\\s+", " ").toUpperCase(Locale.ROOT);
        return t.equals("STOP") || t.equals("STOP ALL") || t.equals("UNSUBSCRIBE");
    }

    // ------------------------------------------------------------------ helpers

    /** Days until the next birthday (0 = today); 29 February counts as 28 February in other years. */
    public static Integer birthdayIn(String birthday, LocalDate today) {
        if (birthday == null || birthday.isBlank()) {
            return null;
        }
        MonthDay md = MonthDay.parse("--" + birthday);
        LocalDate next = on(md, today.getYear());
        if (next.isBefore(today)) {
            next = on(md, today.getYear() + 1);
        }
        return (int) ChronoUnit.DAYS.between(today, next);
    }

    private static LocalDate on(MonthDay md, int year) {
        return md.isValidYear(year) ? md.atYear(year) : LocalDate.of(year, 2, 28);
    }

    private CustomerRow row(Customer c, Object[] totals, LocalDate today) {
        long n = totals == null ? 0 : ((Number) totals[1]).longValue();
        BigDecimal spent = totals == null || totals[2] == null ? BigDecimal.ZERO : (BigDecimal) totals[2];
        return new CustomerRow(c.getCode(), c.getName(), c.getCountry(), c.getEmail(), c.getWhatsapp(), c.getBirthday(),
                birthdayIn(c.getBirthday(), today), n, spent, c.isConsent(), c.getConsentAt(), c.isUnsubscribed(),
                c.getUnsubscribedAt(), c.canMessage(), c.getJoinedAt());
    }

    private Map<Long, Object[]> spent() {
        Map<Long, Object[]> by = new HashMap<>();
        for (Object[] r : sales.spentPerCustomer(current.scope().branchId())) {
            by.put((Long) r[0], r);
        }
        return by;
    }

    private Customer.Details details(CustomerDetailsForm f, boolean consent) {
        return Customers.check(new Customer.Details(f.name(), f.country(), f.email(), f.whatsapp(), f.birthday(), consent));
    }

    /** Passport number: letters and digits (spaces and dashes dropped), upper case; empty = none / keep. */
    static String passport(String s) {
        if (s == null || s.isBlank()) {
            return null;
        }
        String p = s.replaceAll("[\\s-]", "").toUpperCase(Locale.ROOT);
        if (!PASSPORT.matcher(p).matches()) {
            throw ApiException.field(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_FAILED, "The passport number looks wrong.",
                    "customer.passport", "Letters and digits, 4 to 20.");
        }
        return p;
    }

    Customer require(String code) {
        return customers.findByCode(code == null ? "" : code.trim().toUpperCase(Locale.ROOT))
                .orElseThrow(() -> ApiException.notFound("No customer " + code + "."));
    }

    private Customer byToken(String token) {
        if (token == null || token.length() < 20 || token.length() > 64) {
            throw ApiException.notFound("This unsubscribe link is not valid.");
        }
        return customers.findByUnsubToken(token).orElseThrow(() -> ApiException.notFound("This unsubscribe link is not valid."));
    }

    private String shopName() {
        return shops.current().map(ShopSettings::getName).orElse("the shop");
    }

    private static String firstName(String name) {
        String n = name == null ? "" : name.trim();
        int sp = n.indexOf(' ');
        return sp > 0 ? n.substring(0, sp) : n;
    }

    private static void diff(List<String> changed, String what, String before, String after) {
        if (!Objects.equals(before, after)) {
            changed.add(what);
        }
    }

    static String digits(String s) {
        return s == null ? "" : s.replaceAll("\\D", "");
    }
}
