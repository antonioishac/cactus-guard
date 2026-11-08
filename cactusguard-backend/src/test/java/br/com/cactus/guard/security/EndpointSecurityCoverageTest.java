package br.com.cactus.guard.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Deny by default: todo handler de @RestController precisa de @PreAuthorize (metodo ou classe),
 * exceto login/refresh/logout e o health basico.
 */
@SpringBootTest
@ActiveProfiles("test")
class EndpointSecurityCoverageTest {

    private static final Set<String> PUBLIC_HANDLERS = Set.of(
            "AuthController#login", "AuthController#refresh", "AuthController#logout", "HealthController#health");

    @Autowired
    private RequestMappingHandlerMapping handlerMapping;

    @Test
    void everyRestEndpointHasPreAuthorize() {
        Set<String> missing = new TreeSet<>();
        for (var entry : handlerMapping.getHandlerMethods().entrySet()) {
            RequestMappingInfo info = entry.getKey();
            HandlerMethod hm = entry.getValue();
            Class<?> type = hm.getBeanType();
            if (!AnnotatedElementUtils.hasAnnotation(type, RestController.class)
                    || !type.getPackageName().startsWith("br.com.cactus.guard")) {
                continue;
            }
            String id = type.getSimpleName() + "#" + hm.getMethod().getName();
            if (PUBLIC_HANDLERS.contains(id)) {
                continue;
            }
            boolean secured = AnnotatedElementUtils.hasAnnotation(hm.getMethod(), PreAuthorize.class)
                    || AnnotatedElementUtils.hasAnnotation(type, PreAuthorize.class);
            if (!secured) {
                missing.add(id + " " + info);
            }
        }
        assertTrue(missing.isEmpty(), "Endpoints sem @PreAuthorize: " + missing);
    }
}
