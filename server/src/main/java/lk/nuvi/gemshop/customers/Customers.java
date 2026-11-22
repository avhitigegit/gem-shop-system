package lk.nuvi.gemshop.customers;

import java.time.Instant;
import java.time.MonthDay;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lk.nuvi.gemshop.audit.log.ActivityLog;
import lk.nuvi.gemshop.common.error.ApiException;
import lk.nuvi.gemshop.common.error.ErrorCode;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Customers for New sale (BRD F5, J2): pick one who came before, or add a new one with name and country. The full
 * customer screens come with P4-03.
 */
@Service
public class Customers {

    private static final Pattern CODE = Pattern.compile("C-(\\d+)");
    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    private static final Pattern BIRTHDAY = Pattern.compile("^(\\d{2})-(\\d{2})$");

    private final CustomerRepository customers;
    private final ActivityLog log;

    public Customers(CustomerRepository customers, ActivityLog log) {
        this.customers = customers;
        this.log = log;
    }

    @Transactional(readOnly = true)
    public List<Customer> all() {
        return customers.findAllByOrderByNameAsc();
    }

    @Transactional(readOnly = true)
    public Customer require(String code) {
        String c = code == null ? "" : code.trim().toUpperCase(Locale.ROOT);
        return customers.findByCode(c).orElseThrow(() -> ApiException.field(HttpStatus.BAD_REQUEST,
                ErrorCode.VALIDATION_FAILED, "Select the customer.", "customer", "Unknown customer."));
    }

    /** Adds a customer from the New sale form (field errors are "customer.name" …). Joins the sale's transaction. */
    @Transactional(propagation = Propagation.MANDATORY)
    public Customer add(Customer.Details d, Instant at) {
        Customer.Details clean = check(d);
        Customer c = customers.save(new Customer(nextCode(), clean, at));
        log.add("Customer added", c.getName() + " · " + c.getCountry() + (c.isConsent() ? " · consent given" : ""), null);
        return c;
    }

    /** Checks and tidies the form: name and country required, email shape, birthday day + month (or none). */
    public static Customer.Details check(Customer.Details d) {
        String name = trim(d.name());
        String country = trim(d.country());
        if (name == null) {
            throw field("Enter the customer's name.", "customer.name", "Required");
        }
        if (country == null) {
            throw field("Choose the customer's country.", "customer.country", "Required");
        }
        if (name.length() > 120) {
            throw field("The name is too long.", "customer.name", "Up to 120 characters.");
        }
        String email = trim(d.email());
        if (email != null && (email.length() > 160 || !EMAIL.matcher(email).matches())) {
            throw field("The email address looks wrong.", "customer.email", "e.g. anna@example.com");
        }
        String wa = trim(d.whatsapp());
        if (wa != null && (wa.length() > 40 || !wa.matches("^[+0-9 ()-]{6,40}$"))) {
            throw field("The WhatsApp number looks wrong.", "customer.whatsapp", "e.g. +49 151 2345 6789");
        }
        return new Customer.Details(name, country.length() > 60 ? country.substring(0, 60) : country,
                email == null ? null : email.toLowerCase(Locale.ROOT), wa, birthday(d.birthday()), d.consent());
    }

    /** "MM-DD" with a real day (29 February is allowed), or null. */
    static String birthday(String s) {
        String b = trim(s);
        if (b == null) {
            return null;
        }
        Matcher m = BIRTHDAY.matcher(b);
        try {
            if (m.matches()) {
                MonthDay.of(Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)));
                return b;
            }
        } catch (RuntimeException e) {
            // falls through
        }
        throw field("The birthday looks wrong – choose both the day and the month, or leave both empty.", "customer.birthday",
                "Day and month");
    }

    private String nextCode() {
        int last = 0;
        for (String c : customers.allCodes()) {
            Matcher m = CODE.matcher(c);
            if (m.matches()) {
                last = Math.max(last, Integer.parseInt(m.group(1)));
            }
        }
        return "C-%03d".formatted(last + 1);
    }

    private static ApiException field(String msg, String field, String fieldMsg) {
        return ApiException.field(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_FAILED, msg, field, fieldMsg);
    }

    private static String trim(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
