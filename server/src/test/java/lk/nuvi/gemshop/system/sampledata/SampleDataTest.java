package lk.nuvi.gemshop.system.sampledata;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class SampleDataTest {

    private final JsonMapper json = JsonMapper.builder().build();

    private JsonNode root() {
        return json.readTree("{\"exportedOn\":\"2026-11-22\",\"state\":{}}");
    }

    /** A sample "today" with the clock at the end of that day, so its moments are not moved. */
    private SampleData data(String today) {
        return new SampleData(root(), LocalDate.parse(today), Instant.parse(today + "T23:59:59Z"));
    }

    @Test
    void datesMoveForwardToToday() {
        SampleData d = data("2026-12-02");
        assertThat(d.shiftDays()).isEqualTo(10);
        assertThat(d.date(json.readTree("\"2027-01-06\""))).isEqualTo(LocalDate.parse("2027-01-16"));
        assertThat(d.instant(json.readTree("\"2026-11-22T03:15:00.000Z\"")))
                .isEqualTo(Instant.parse("2026-12-02T03:15:00Z"));
    }

    @Test
    void sampleMomentsAreNeverInTheFuture() {
        // just after midnight in the shop: "today 03:15 UTC" from the prototype has not happened yet
        Instant now = Instant.parse("2026-12-01T18:40:00Z");
        SampleData d = new SampleData(root(), LocalDate.parse("2026-12-02"), now);
        assertThat(d.instant(json.readTree("\"2026-11-22T03:15:00.000Z\""))).isEqualTo(now);
        assertThat(d.instant(json.readTree("\"2026-11-21T03:15:00.000Z\""))).isEqualTo(Instant.parse("2026-12-01T03:15:00Z"));
    }

    @Test
    void birthdaysMoveAndCrossTheYearEnd() {
        SampleData d = data("2026-12-02");
        assertThat(d.birthday(json.readTree("\"11-25\""))).isEqualTo("12-05");
        assertThat(d.birthday(json.readTree("\"12-28\""))).isEqualTo("01-07");
    }

    @Test
    void sameDayMeansNoChange() {
        SampleData d = data("2026-11-22");
        assertThat(d.date(json.readTree("\"2026-11-09\""))).isEqualTo(LocalDate.parse("2026-11-09"));
        assertThat(d.birthday(json.readTree("\"02-29\""))).isEqualTo("02-29");
    }

    @Test
    void missingValuesAreNull() {
        SampleData d = data("2026-11-22");
        assertThat(d.date(json.readTree("null"))).isNull();
        assertThat(SampleData.text(json.readTree("{}").path("x"))).isNull();
    }
}
