package lk.nuvi.gemshop.system.sampledata;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

/** Prototype alert links → app pages, and dates inside texts moving with the sample data. */
class AuditSampleDataTest {

    @Test
    void prototypeLinksBecomeAppPages() {
        assertThat(AuditSampleData.link("g-payments/invoice.html?id=INV-000138", "AL-INV-000138")).isEqualTo("/sell/invoices/INV-000138");
        assertThat(AuditSampleData.link("k-owner-control/approvals.html", "AL-AP-0012")).isEqualTo("/approvals#AP-0012");
        assertThat(AuditSampleData.link("a-setup/shop-vat.html", "AL-NGJA")).isEqualTo("/settings/shop");
        assertThat(AuditSampleData.link("l-reports/daily-closing.html", "AL-DC-20261121")).isEqualTo("/reports/daily-cash");
        assertThat(AuditSampleData.link("e-returns/credit-note.html?id=CN-0001", "AL-1")).isEqualTo("/sell/credit-notes/CN-0001");
        assertThat(AuditSampleData.link("", "AL-1")).isNull();
        assertThat(AuditSampleData.link("x/unknown.html", "AL-1")).isNull();
    }

    @Test
    void datesInTextsMoveWithTheSampleData() throws Exception {
        var root = JsonMapper.builder().build().readTree("{\"exportedOn\":\"2026-11-22\",\"state\":{}}");
        SampleData tenDaysLater = new SampleData(root, LocalDate.of(2026, 12, 2));
        assertThat(AuditSampleData.shift("Day closing 21/11/2026 · balanced", tenDaysLater)).isEqualTo("Day closing 01/12/2026 · balanced");
        assertThat(AuditSampleData.shift("DC-20261121", tenDaysLater)).isEqualTo("DC-20261201");
        assertThat(AuditSampleData.shift("NGJA dealer licence expires in 45 days – renew before 06/01/2027", tenDaysLater))
                .endsWith("renew before 16/01/2027");
        SampleData sameDay = new SampleData(root, LocalDate.of(2026, 11, 22));
        assertThat(AuditSampleData.shift("DC-20261121", sameDay)).isEqualTo("DC-20261121");
    }
}
