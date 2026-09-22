package com.railway.controller;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class MetricsControllerTest {

    private final MetricsController controller = new MetricsController();

    @Test
    void shouldExposePrometheusTextFormat() {
        String body = controller.metrics();
        assertTrue(body.contains("# TYPE railway_up gauge"));
        assertTrue(body.contains("railway_up 1"));
        assertTrue(body.contains("railway_jvm_heap_used_bytes"));
        assertTrue(body.contains("railway_jvm_threads"));
        assertTrue(body.contains("railway_jvm_uptime_seconds"));
        assertTrue(body.contains("# TYPE railway_jvm_gc_collections_total counter"));
        assertTrue(body.contains("railway_system_processors"));
    }
}
