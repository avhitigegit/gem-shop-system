package lk.nuvi.gemshop.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import lk.nuvi.gemshop.TestcontainersConfiguration;
import lk.nuvi.gemshop.auth.security.PublicApi;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/**
 * "Role and branch check on every API": every API of the app must say who may call it
 * (@PreAuthorize), or be marked @PublicApi. A new API without it makes this test fail.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class EndpointRolesTest {

    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    RequestMappingHandlerMapping mapping;

    @Test
    void everyApiDeclaresWhoMayCallIt() {
        List<String> missing = new ArrayList<>();
        int checked = 0;
        for (var e : mapping.getHandlerMethods().entrySet()) {
            Class<?> type = e.getValue().getBeanType();
            Method m = e.getValue().getMethod();
            if (!type.getName().startsWith("lk.nuvi.gemshop.") || isTestClass(type)) {
                continue;
            }
            checked++;
            boolean declared = AnnotatedElementUtils.hasAnnotation(m, PreAuthorize.class)
                    || AnnotatedElementUtils.hasAnnotation(type, PreAuthorize.class)
                    || AnnotatedElementUtils.hasAnnotation(m, PublicApi.class)
                    || AnnotatedElementUtils.hasAnnotation(type, PublicApi.class);
            if (!declared) {
                missing.add(type.getSimpleName() + "." + m.getName() + " " + e.getKey());
            }
        }
        assertThat(checked).isGreaterThan(5);
        assertThat(missing).as("APIs without @PreAuthorize or @PublicApi").isEmpty();
    }

    private static boolean isTestClass(Class<?> type) {
        var src = type.getProtectionDomain().getCodeSource();
        return src != null && src.getLocation().toString().contains("test-classes");
    }
}
