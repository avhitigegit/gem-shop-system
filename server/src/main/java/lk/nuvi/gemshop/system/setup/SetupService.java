package lk.nuvi.gemshop.system.setup;

import java.text.Normalizer;
import java.time.Instant;
import java.util.Locale;
import lk.nuvi.gemshop.audit.log.ActivityLog;
import lk.nuvi.gemshop.auth.AuthService;
import lk.nuvi.gemshop.auth.PasswordRules;
import lk.nuvi.gemshop.auth.user.Role;
import lk.nuvi.gemshop.auth.user.UserAccount;
import lk.nuvi.gemshop.auth.user.UserAccountRepository;
import lk.nuvi.gemshop.common.error.ApiException;
import lk.nuvi.gemshop.common.mail.MailService;
import lk.nuvi.gemshop.common.money.Currency;
import lk.nuvi.gemshop.settings.branch.Branch;
import lk.nuvi.gemshop.settings.branch.BranchRepository;
import lk.nuvi.gemshop.settings.branch.BranchStatus;
import lk.nuvi.gemshop.settings.rates.ExchangeRate;
import lk.nuvi.gemshop.settings.rates.ExchangeRateRepository;
import lk.nuvi.gemshop.settings.shop.ShopSettings;
import lk.nuvi.gemshop.settings.shop.ShopSettingsRepository;
import lk.nuvi.gemshop.system.setup.SetupController.SetupRequest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Creates the owner, the shop, the first branch and today's rates on an empty system. */
@Service
public class SetupService {

    public static final String SETUP_DONE = "SETUP_DONE";

    private final UserAccountRepository users;
    private final BranchRepository branches;
    private final ShopSettingsRepository shops;
    private final ExchangeRateRepository rates;
    private final PasswordEncoder encoder;
    private final AuthService auth;
    private final MailService mail;
    private final ActivityLog activity;

    public SetupService(UserAccountRepository users, BranchRepository branches, ShopSettingsRepository shops,
            ExchangeRateRepository rates, PasswordEncoder encoder, AuthService auth, MailService mail, ActivityLog activity) {
        this.activity = activity;
        this.users = users;
        this.branches = branches;
        this.shops = shops;
        this.rates = rates;
        this.encoder = encoder;
        this.auth = auth;
        this.mail = mail;
    }

    @Transactional(readOnly = true)
    public boolean needed() {
        return users.count() == 0;
    }

    @Transactional(readOnly = true)
    public String shopName() {
        return shops.current().map(ShopSettings::getName).orElse(null);
    }

    @Transactional
    public AuthService.Result run(SetupRequest r, String device) {
        if (!needed()) {
            throw ApiException.conflict(SETUP_DONE, "The shop is already set up. Please sign in.");
        }
        PasswordRules.check(r.owner().password(), "owner.password");
        Instant now = Instant.now();
        Branch branch = branches.findByCode(code(r.branch().name()))
                .orElseGet(() -> branches.save(new Branch(code(r.branch().name()), r.branch().name().trim(), BranchStatus.ACTIVE)
                        .details(null, r.shop().address().trim(), r.owner().phone().trim(), java.time.LocalDate.now())));
        if (shops.current().isEmpty()) {
            shops.save(new ShopSettings(r.shop().name().trim())
                    .contact(null, r.shop().address().trim(), null, null, null)
                    .licence(r.shop().ngjaLicenceNo().trim(), r.shop().ngjaLicenceExpiry(), 60, true));
        }
        rates.save(new ExchangeRate(Currency.USD, r.rates().usd(), now));
        rates.save(new ExchangeRate(Currency.EUR, r.rates().eur(), now));
        UserAccount owner = new UserAccount(r.owner().name().trim(), r.owner().email(), Role.OWNER, null).phone(r.owner().phone().trim());
        owner.changePassword(encoder.encode(r.owner().password()), now);
        users.save(owner);
        activity.addFor(owner, "Shop set up", r.shop().name().trim() + " · first branch " + branch.getName() + " · USD "
                + r.rates().usd().toPlainString() + " · EUR " + r.rates().eur().toPlainString(), null);
        mail.send(owner.getEmail(), "Your owner account is ready", """
                Hello %s,

                %s is set up in the Gem Shop System.
                First branch: %s

                You can now add staff logins, the second branch and the drop-down lists in Settings.
                """.formatted(owner.getName().split(" ")[0], r.shop().name().trim(), branch.getName()));
        return auth.signIn(owner, false, device, now);
    }

    /** "Ella" → "ella", "Branch 2 – Kandy" → "branch-2-kandy". */
    static String code(String name) {
        String s = Normalizer.normalize(name, Normalizer.Form.NFD).replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("^-+|-+$", "");
        if (s.isEmpty()) {
            s = "main";
        }
        return s.length() > 20 ? s.substring(0, 20).replaceAll("-+$", "") : s;
    }
}
