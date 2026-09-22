package com.railway.apidoc;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class ApiDocCoverageTest {

    @Autowired
    private RequestMappingHandlerMapping handlerMapping;

    @Autowired
    private ApiDocRegistry apiDocRegistry;

    @Test
    void everyControllerMappingShouldBeDocumented() {
        Set<String> actual = new TreeSet<>();
        for (RequestMappingInfo info : handlerMapping.getHandlerMethods().keySet()) {
            Set<String> patterns = new LinkedHashSet<>();
            if (info.getPathPatternsCondition() != null) {
                patterns.addAll(info.getPathPatternsCondition().getPatternValues());
            }
            for (String pattern : patterns) {
                if (pattern.startsWith("/error")) {
                    continue;
                }
                for (RequestMethod method : info.getMethodsCondition().getMethods()) {
                    actual.add(method.name() + " " + pattern);
                }
            }
        }
        assertFalse(actual.isEmpty());

        Set<String> missing = new TreeSet<>(actual);
        missing.removeAll(apiDocRegistry.documentedKeys());
        assertTrue(missing.isEmpty(), "存在未文档化接口: " + missing);

        assertEquals(actual.size(), apiDocRegistry.documentedKeys().size(),
                "文档数量与实际映射数量不一致");
    }

    @Test
    void openApiShouldContainCorePathsAndTags() {
        var openApi = apiDocRegistry.toOpenApi();
        assertEquals("3.0.3", openApi.path("openapi").asText());
        assertTrue(openApi.path("paths").has("/api/trains"));
        assertTrue(openApi.path("paths").has("/api/order/grab"));
        assertTrue(openApi.path("paths").has("/v3/api-docs"));
        assertTrue(openApi.path("tags").isArray());
        assertTrue(openApi.path("tags").size() >= 8);
    }
}
