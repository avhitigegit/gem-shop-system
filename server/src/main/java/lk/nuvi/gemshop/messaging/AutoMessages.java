package lk.nuvi.gemshop.messaging;

import jakarta.annotation.PreDestroy;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;
import lk.nuvi.gemshop.audit.log.ActivityLog;
import lk.nuvi.gemshop.common.config.AppProperties;
import lk.nuvi.gemshop.customers.Customer;
import lk.nuvi.gemshop.sales.Invoices;
import lk.nuvi.gemshop.sales.Sale;
import lk.nuvi.gemshop.sales.SaleRepository;
import lk.nuvi.gemshop.sales.Sales;
import lk.nuvi.gemshop.settings.shop.ShopSettings;
import lk.nuvi.gemshop.settings.shop.ShopSettingsRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Automatic messages to customers (BRD J5): the invoice PDF by email / WhatsApp right after the sale, and a thank-you message
 * the day after. Each follows its switch on the Email &amp; WhatsApp settings screen and the email / WhatsApp on-off switches.
 * Sending happens in the background after the sale is saved, so the counter never waits for the mail server. Logged as
 * "System (automatic)". (The day-end report to the owner is sent by Close the day.)
 */
@Component
public class AutoMessages {

    private static final Logger LOG = LoggerFactory.getLogger(AutoMessages.class);

    private final Messages messages;
    private final SaleRepository sales;
    private final Invoices invoices;
    private final ShopSettingsRepository shops;
    private final ActivityLog log;
    private final TransactionTemplate tx;
    private final ZoneId zone;
    private final ExecutorService background = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "auto-messages");
        t.setDaemon(true);
        return t;
    });

    public AutoMessages(Messages messages, SaleRepository sales, Invoices invoices, ShopSettingsRepository shops, ActivityLog log,
            PlatformTransactionManager txm, AppProperties app) {
        this.messages = messages;
        this.sales = sales;
        this.invoices = invoices;
        this.shops = shops;
        this.log = log;
        this.tx = new TransactionTemplate(txm);
        this.zone = ZoneId.of(app.timezone());
    }

    @PreDestroy
    void stop() {
        background.shutdown();
    }

    // ------------------------------------------------------------------ the invoice after the sale

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onSale(Sales.Completed e) {
        background.execute(() -> {
            try {
                tx.executeWithoutResult(st -> sendInvoice(e.number()));
            } catch (RuntimeException ex) {
                LOG.warn("Invoice {} not sent automatically: {}", e.number(), ex.toString());
            }
        });
    }

    /** Sends the invoice PDF to the customer when the switches say so. Returns what was sent. */
    void sendInvoice(String number) {
        MessageSettings m = messages.settings();
        Sale s = sales.findByNumber(number).orElse(null);
        if (s == null || s.getStatus() != Sale.Status.COMPLETED) {
            return;
        }
        Customer c = s.getCustomer();
        boolean email = m.emailOn() && m.auto().invoiceEmail() && present(c.getEmail());
        boolean wa = m.whatsappReady() && m.auto().invoiceWhatsapp() && present(c.getWhatsapp());
        if (!email && !wa) {
            return;
        }
        byte[] pdf = invoices.pdf(s);
        String shop = shopName();
        String file = "Invoice " + s.getNumber() + ".pdf";
        String text = "Dear " + first(c.getName()) + ",\n\nThank you for visiting " + shop + ", " + s.getBranch().getDisplayName()
                + ". Please find your invoice " + s.getNumber() + " attached as a PDF.\n\nSafe travels!\n" + shop;
        boolean mailed = email && messages.email(Messages.Kind.INVOICE, c.getId(), c.getEmail(), s.getNumber(),
                "Your invoice " + s.getNumber() + " – " + shop, text, file, pdf, true);
        boolean sentWa = wa && messages.whatsapp(Messages.Kind.INVOICE, c.getId(), c.getWhatsapp(), s.getNumber(), text, file, pdf, true);
        if (mailed || sentWa) {
            s.sent(mailed, sentWa);
            log.addSystem("Invoice sent automatically", s.getNumber() + " to " + c.getName() + " by "
                    + (mailed && sentWa ? "email and WhatsApp" : mailed ? "email" : "WhatsApp"), s.getBranch().getId());
        }
    }

    // ------------------------------------------------------------------ thank-you the next day

    /** Every hour from 10:00 to 20:00 (so a server restart does not miss the day): yesterday's buyers get a thank-you. */
    @Scheduled(cron = "0 0 10-20 * * *", zone = "Asia/Colombo")
    public void thankYouJob() {
        try {
            Integer n = tx.execute(st -> thankYou(LocalDate.now(zone).minusDays(1)));
            if (n != null && n > 0) {
                LOG.info("{} thank-you message(s) sent", n);
            }
        } catch (RuntimeException e) {
            LOG.warn("Thank-you messages not sent: {}", e.toString());
        }
    }

    /** Thank-you for the completed sales of one day that did not get one yet. Returns how many sales got one. */
    public int thankYou(LocalDate day) {
        MessageSettings m = messages.settings();
        if (!m.auto().thankYou() || (!m.emailOn() && !m.whatsappReady())) {
            return 0;
        }
        List<Sale> list = sales.search(null, day.atStartOfDay(zone).toInstant(), day.plusDays(1).atStartOfDay(zone).toInstant(),
                Sale.Status.COMPLETED);
        String shop = shopName();
        int n = 0;
        for (Sale s : list) {
            if (messages.logged(Messages.Kind.THANK_YOU, s.getNumber())) {
                continue;
            }
            Customer c = s.getCustomer();
            String gems = s.getItems().stream().filter(i -> !i.isReleased()).map(i -> i.getGem().getName()).distinct()
                    .collect(Collectors.joining(", "));
            String text = "Dear " + first(c.getName()) + ",\n\nThank you for visiting " + shop + ", " + s.getBranch().getDisplayName()
                    + " yesterday." + (gems.isEmpty() ? "" : " We hope you enjoy your " + gems.toLowerCase(Locale.ROOT) + ".")
                    + "\n\nIf you have any question about your gems, just reply to this message.\n\nSafe travels!\n" + shop;
            boolean any = false;
            if (m.emailOn() && present(c.getEmail())) {
                any |= messages.email(Messages.Kind.THANK_YOU, c.getId(), c.getEmail(), s.getNumber(), "Thank you from " + shop, text,
                        null, null, true);
            }
            if (m.whatsappReady() && present(c.getWhatsapp())) {
                any |= messages.whatsapp(Messages.Kind.THANK_YOU, c.getId(), c.getWhatsapp(), s.getNumber(), text, null, null, true);
            }
            if (any) {
                n++;
            }
        }
        if (n > 0) {
            log.addSystem("Thank-you messages sent", n + " customer(s) who bought on " + day, null);
        }
        return n;
    }

    private String shopName() {
        return shops.current().map(ShopSettings::getName).orElse("our shop");
    }

    private static String first(String name) {
        return name == null ? "" : name.trim().split("\\s+")[0];
    }

    private static boolean present(String s) {
        return s != null && !s.isBlank();
    }
}
