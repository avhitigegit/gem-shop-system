package lk.nuvi.gemshop;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import lk.nuvi.gemshop.common.error.ApiException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** P0-02: health, Flyway, OpenAPI and the one error format. */
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, SkeletonTest.TestApi.class})
class SkeletonTest {

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;

    @Test
    void healthSaysApiAndDatabaseAreUp() throws Exception {
        mvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.database").value("UP"))
                .andExpect(jsonPath("$.app").value("Gem Shop System"))
                .andExpect(jsonPath("$.environment").value("local"));
    }

    @Test
    void flywayCreatedTheFirstTable() {
        String v = jdbc.queryForObject("select meta_value from app_meta where meta_key = 'schema_started'", String.class);
        assertThat(v).isEqualTo("P0-02 Project skeleton");
    }

    @Test
    void openApiListsTheHealthEndpoint() throws Exception {
        mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/health']").exists())
                .andExpect(jsonPath("$.components.schemas.HealthResponse").exists())
                .andExpect(jsonPath("$.components.schemas.ApiError").exists())
                .andExpect(jsonPath("$.paths['/api/health'].get.responses.default").exists());
    }

    @Test
    void unknownPathUsesErrorFormat() throws Exception {
        mvc.perform(get("/api/does-not-exist"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.path").value("/api/does-not-exist"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void invalidFormListsTheFields() throws Exception {
        mvc.perform(post("/api/test/form").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"\",\"price\":-5}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors.length()").value(2));
    }

    @Test
    void brokenJsonIsBadRequest() throws Exception {
        mvc.perform(post("/api/test/form").contentType(MediaType.APPLICATION_JSON).content("{oops"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
    }

    @Test
    void wrongMethodUsesErrorFormat() throws Exception {
        mvc.perform(post("/api/health"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"));
    }

    @Test
    void businessErrorKeepsItsCode() throws Exception {
        mvc.perform(get("/api/test/business"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BELOW_MIN_PRICE"))
                .andExpect(jsonPath("$.message").value("Price is below the minimum."));
    }

    @Test
    void unexpectedErrorHidesDetails() throws Exception {
        mvc.perform(get("/api/test/crash"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.message").value(containsString("Something went wrong")));
    }

    record Form(@NotBlank String name, @Positive BigDecimal price) {}

    @RestController
    static class TestApi {
        @PostMapping("/api/test/form")
        Form form(@Valid @RequestBody Form f) {
            return f;
        }

        @GetMapping("/api/test/business")
        String business() {
            throw new ApiException(HttpStatus.BAD_REQUEST, "BELOW_MIN_PRICE", "Price is below the minimum.");
        }

        @GetMapping("/api/test/crash")
        String crash() {
            throw new IllegalStateException("secret detail");
        }
    }
}
