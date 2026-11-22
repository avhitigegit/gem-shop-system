package lk.nuvi.gemshop.system.sampledata;

import java.io.IOException;
import java.io.InputStream;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lk.nuvi.gemshop.common.config.AppProperties;
import lk.nuvi.gemshop.settings.branch.BranchRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Loads the prototype demo data when the backend starts on an EMPTY database.
 * <ul>
 *   <li>Only when {@code app.sample-data.enabled=true} (local, dev, uat – never prod).</li>
 *   <li>Refuses to run when {@code app.environment=prod}, even if switched on by mistake.</li>
 *   <li>Runs only if there are no branches yet, so real data is never touched.</li>
 * </ul>
 * Fresh sample data at any time: {@code infra/scripts/reset-data.sh} (empties the local database).
 */
@Component
@ConditionalOnProperty(name = "app.sample-data.enabled", havingValue = "true")
public class SampleDataLoader {

    public static final String FILE = "sample-data/prototype-v12.json";
    private static final Logger log = LoggerFactory.getLogger(SampleDataLoader.class);

    private final List<SampleDataPart> parts;
    private final BranchRepository branches;
    private final AppProperties app;
    private final ObjectMapper json;

    public SampleDataLoader(List<SampleDataPart> parts, BranchRepository branches, AppProperties app, ObjectMapper json) {
        this.parts = parts.stream().sorted(Comparator.comparingInt(SampleDataPart::order)).toList();
        this.branches = branches;
        this.app = app;
        this.json = json;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void loadOnStartup() {
        if (branches.count() > 0) {
            Map<String, Integer> added = catchUp();
            log.info("Sample data: database already has data{}", added.isEmpty() ? " – nothing loaded" : " – added what was missing: " + added);
            return;
        }
        Map<String, Integer> counts = load();
        log.info("Sample data loaded (prototype v12): {}", counts);
    }

    /** Loads every part. Callers must make sure the database is empty. */
    @Transactional
    public Map<String, Integer> load() {
        if ("prod".equalsIgnoreCase(app.environment())) {
            throw new IllegalStateException("Sample data is never loaded in production");
        }
        SampleData data = new SampleData(read(), SampleData.today(ZoneId.of(app.timezone())));
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (SampleDataPart part : parts) {
            counts.put(part.name(), part.load(data));
        }
        return counts;
    }

    /** Parts added after the database was filled (P1-02 rates and sample passwords). */
    @Transactional
    public Map<String, Integer> catchUp() {
        if ("prod".equalsIgnoreCase(app.environment())) {
            return Map.of();
        }
        SampleData data = new SampleData(read(), SampleData.today(ZoneId.of(app.timezone())));
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (SampleDataPart part : parts) {
            int n = part.catchUp(data);
            if (n > 0) {
                counts.put(part.name(), n);
            }
        }
        return counts;
    }

    private JsonNode read() {
        try (InputStream in = new ClassPathResource(FILE).getInputStream()) {
            return json.readTree(in);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read " + FILE, e);
        }
    }
}
