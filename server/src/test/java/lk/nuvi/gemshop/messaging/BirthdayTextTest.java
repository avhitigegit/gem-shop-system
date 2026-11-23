package lk.nuvi.gemshop.messaging;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** P4-04: {name} is the first name, {shop} the shop name (no database). */
class BirthdayTextTest {

    @Test
    void fillsNameAndShop() {
        assertThat(Birthdays.fill("Happy birthday, {name}! Warm wishes from {shop}.", "Anna Müller", "Ceylon Gem House"))
                .isEqualTo("Happy birthday, Anna! Warm wishes from Ceylon Gem House.");
        assertThat(Birthdays.fill("{shop} says {name} {name}", "  Lukas  Schneider ", "Shop")).isEqualTo("Shop says Lukas Lukas");
        assertThat(Birthdays.fill(null, "Anna", "Shop")).isEmpty();
        assertThat(Messages.hhmm(java.time.LocalTime.of(9, 5))).isEqualTo("09:05");
    }
}
