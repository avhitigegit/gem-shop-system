package lk.nuvi.gemshop.customers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import lk.nuvi.gemshop.common.error.ApiException;
import org.junit.jupiter.api.Test;

/** P4-03: birthday countdown, WhatsApp STOP words and passport format (no database). */
class CustomerRulesTest {

    @Test
    void daysUntilTheNextBirthday() {
        LocalDate today = LocalDate.of(2026, 11, 23);
        assertThat(CustomerBook.birthdayIn("11-23", today)).isZero();
        assertThat(CustomerBook.birthdayIn("11-25", today)).isEqualTo(2);
        assertThat(CustomerBook.birthdayIn("11-22", today)).isEqualTo(364);
        assertThat(CustomerBook.birthdayIn("01-01", today)).isEqualTo(39);
        assertThat(CustomerBook.birthdayIn(null, today)).isNull();
        // 29 February: 28 February in a year without it
        assertThat(CustomerBook.birthdayIn("02-29", LocalDate.of(2027, 2, 28))).isZero();
        assertThat(CustomerBook.birthdayIn("02-29", LocalDate.of(2028, 2, 28))).isEqualTo(1);
    }

    @Test
    void stopWords() {
        assertThat(CustomerBook.isStop("STOP")).isTrue();
        assertThat(CustomerBook.isStop(" stop ")).isTrue();
        assertThat(CustomerBook.isStop("Stop!")).isTrue();
        assertThat(CustomerBook.isStop("stop all")).isTrue();
        assertThat(CustomerBook.isStop("Unsubscribe")).isTrue();
        assertThat(CustomerBook.isStop("please don't stop")).isFalse();
        assertThat(CustomerBook.isStop("")).isFalse();
        assertThat(CustomerBook.isStop(null)).isFalse();
        assertThat(CustomerBook.digits("+49 151 2345-6789")).isEqualTo("4915123456789");
    }

    @Test
    void passportFormat() {
        assertThat(CustomerBook.passport(" ya 123-4567 ")).isEqualTo("YA1234567");
        assertThat(CustomerBook.passport("")).isNull();
        assertThatThrownBy(() -> CustomerBook.passport("ab")).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> CustomerBook.passport("AB#12345")).isInstanceOf(ApiException.class);
    }
}
