package lk.nuvi.gemshop.settings.shop;

import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import lk.nuvi.gemshop.audit.log.ActivityLog;
import lk.nuvi.gemshop.common.config.AppProperties;
import lk.nuvi.gemshop.files.FileKind;
import lk.nuvi.gemshop.files.FileService;
import lk.nuvi.gemshop.files.StoredFile;
import lk.nuvi.gemshop.settings.api.SettingsDtos.ShopForm;
import lk.nuvi.gemshop.settings.api.SettingsDtos.ShopView;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Shop details (BRD A3, §08, M6): printed on every invoice and certificate; can be changed without new software
 * (NFR-8). No VAT (prices include it). The card bank fee is a shop cost – the customer pays the normal price.
 */
@Service
public class ShopService {

    /** Files of the shop itself (the logo) are stored under this owner. */
    public static final String OWNER_TYPE = "SHOP";
    public static final String OWNER_REF = "shop";

    private final ShopSettingsRepository shops;
    private final FileService files;
    private final ActivityLog log;
    private final ZoneId zone;

    public ShopService(ShopSettingsRepository shops, FileService files, ActivityLog log, AppProperties app) {
        this.shops = shops;
        this.files = files;
        this.log = log;
        this.zone = ZoneId.of(app.timezone());
    }

    @Transactional(readOnly = true)
    public ShopView view() {
        return view(current());
    }

    /** Days until the NGJA licence expires (below 0 = expired), or null when no date is set. */
    public Integer licenceDaysLeft(ShopSettings s) {
        return s.getNgjaLicenceExpiry() == null ? null
                : (int) ChronoUnit.DAYS.between(LocalDate.now(zone), s.getNgjaLicenceExpiry());
    }

    @Transactional
    public ShopView save(ShopForm f) {
        ShopSettings s = current();
        s.rename(f.name().trim());
        s.contact(blank(f.tagline()), f.address().trim(), f.phone().trim(), f.email().trim(), blank(f.website()));
        s.licence(f.ngjaLicenceNo().trim(), f.ngjaLicenceExpiry(), f.ngjaRemindDays(), f.printLicenceOnInvoice());
        s.rules(f.largeExpenseLimit().setScale(2, RoundingMode.HALF_UP), f.cardFeePct().setScale(2, RoundingMode.HALF_UP),
                s.getInvoicePrefix(), f.returnPolicyDays());
        String logoNote = "";
        if (f.logoFileId() != null && !f.logoFileId().toString().equals(s.getLogoFile())) {
            removeLogos();
            files.attach(List.of(f.logoFileId()), FileKind.LOGO, OWNER_TYPE, OWNER_REF);
            s.setLogoFile(f.logoFileId().toString());
            logoNote = " · new logo";
        } else if (Boolean.TRUE.equals(f.removeLogo()) && s.getLogoFile() != null) {
            removeLogos();
            s.setLogoFile(null);
            logoNote = " · default logo";
        }
        log.add("Shop settings saved", "Details updated" + logoNote, null);
        return view(s);
    }

    private void removeLogos() {
        files.ofOwner(OWNER_TYPE, OWNER_REF).forEach(files::remove);
    }

    private ShopSettings current() {
        return shops.current().orElseGet(() -> shops.save(new ShopSettings("Gem Shop System")));
    }

    private ShopView view(ShopSettings s) {
        UUID logo = s.getLogoFile() == null ? null : UUID.fromString(s.getLogoFile());
        String url = null;
        if (logo != null) {
            StoredFile f = files.ofOwner(OWNER_TYPE, OWNER_REF).stream().filter(x -> x.getPublicId().equals(logo)).findFirst()
                    .orElse(null);
            url = f == null ? null : files.link(f, false);
        }
        return new ShopView(s.getName(), s.getTagline(), s.getAddress(), s.getPhone(), s.getEmail(), s.getWebsite(), logo, url,
                s.getNgjaLicenceNo(), s.getNgjaLicenceExpiry(), s.getNgjaRemindDays(), s.isPrintLicenceOnInvoice(),
                licenceDaysLeft(s), s.getLargeExpenseLimit(), s.getCardFeePct(), s.getReturnPolicyDays());
    }

    private static String blank(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
