package lk.nuvi.gemshop.offers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import lk.nuvi.gemshop.common.error.ApiException;
import org.junit.jupiter.api.Test;

/** P4-05: picture, audience text and templates (no database). */
class OfferRulesTest {

    @Test
    void photoMustBePngOrJpeg() {
        Offers.Photo p = Offers.photo("data:image/png;base64,iVBORw0KGgo=");
        assertThat(p.type()).isEqualTo("image/png");
        assertThat(p.bytes()).isNotEmpty();
        assertThat(Offers.photo("")).isNull();
        assertThatThrownBy(() -> Offers.photo("data:image/svg+xml;base64,PHN2Zz4=")).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> Offers.photo("data:image/png;base64,!!!")).isInstanceOf(ApiException.class);
    }

    @Test
    void audienceTextAndTemplates() {
        assertThat(Offers.audienceText(List.of(), List.of())).isEqualTo("All with permission");
        assertThat(Offers.audienceText(List.of("Germany"), List.of("Ruby", "Moonstone"))).isEqualTo("Germany · Ruby, Moonstone");
        assertThat(Offers.split("A|B")).containsExactly("A", "B");
        assertThat(Offers.split("")).isEmpty();
        assertThat(Offers.template("festive_offer")).isPresent();
        assertThat(Offers.template("x")).isEmpty();
        assertThat(Offers.first("  Anna Müller")).isEqualTo("Anna");
    }
}
