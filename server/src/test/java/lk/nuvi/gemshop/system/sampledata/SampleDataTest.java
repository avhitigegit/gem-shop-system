package lk.nuvi.gemshop.system.sampledata;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class SampleDataTest {

    private final JsonMapper json = JsonMapper.builder().build();

    private SampleData data(String today) {
        JsonNode root = json.readTree("{\"exportedOn\":\"2026-11-22\",\"state\":{}}");
        return new SampleData(root, LocalDate.parse(today));
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
