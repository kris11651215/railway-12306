package com.railway.deploy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DeploymentConfigTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void dockerfileShouldBeMultiStageWithHealthcheck() throws IOException {
        String dockerfile = read("Dockerfile");
        assertTrue(dockerfile.contains("FROM maven:"));
        assertTrue(dockerfile.contains("FROM eclipse-temurin:17-jre"));
        assertTrue(dockerfile.contains("mvn -B -q -DskipTests package"));
        assertTrue(dockerfile.contains("HEALTHCHECK"));
        assertTrue(dockerfile.contains("EXPOSE 8080"));
    }

    @Test
    void composeShouldOrchestrateCoreServicesAndProfiles() throws IOException {
        Map<String, Object> compose = loadYaml("docker-compose.yml");
        Map<String, Object> services = map(compose.get("services"));
        for (String name : List.of("mysql", "redis", "app", "nginx",
                "prometheus", "grafana", "skywalking-oap", "skywalking-ui",
                "rocketmq-namesrv", "rocketmq-broker")) {
            assertTrue(services.containsKey(name), "缺少服务: " + name);
        }

        Map<String, Object> app = map(services.get("app"));
        Map<String, Object> dependsOn = map(app.get("depends_on"));
        assertEquals("service_healthy", map(dependsOn.get("mysql")).get("condition"));
        assertEquals("service_healthy", map(dependsOn.get("redis")).get("condition"));
        Map<String, Object> environment = map(app.get("environment"));
        assertEquals("redis", environment.get("RAILWAY_REDIS_MODE"));
        assertEquals("db", environment.get("RAILWAY_GRAB_ORDER_STORE"));
        assertEquals("db", environment.get("RAILWAY_ROUTE_MODE"));
        assertEquals("db", environment.get("RAILWAY_AI_DATA_MODE"));

        Map<String, Object> nginx = map(services.get("nginx"));
        assertEquals("service_healthy", map(map(nginx.get("depends_on")).get("app")).get("condition"));

        assertTrue(profilesOf(services, "prometheus").contains("monitoring"));
        assertTrue(profilesOf(services, "skywalking-oap").contains("tracing"));
        assertTrue(profilesOf(services, "rocketmq-namesrv").contains("mq"));
        assertTrue(map(compose.get("volumes")).containsKey("mysql-data"));
    }

    @Test
    void nginxShouldProxyToAppWithDynamicResolver() throws IOException {
        String nginx = read("deploy/nginx/nginx.conf");
        assertTrue(nginx.contains("resolver 127.0.0.11"));
        assertTrue(nginx.contains("proxy_pass $railway_backend"));
        assertTrue(nginx.contains("set $railway_backend http://app:8080"));
        assertTrue(nginx.contains("listen 80"));
        assertTrue(nginx.contains("location /internal/"));
        assertTrue(nginx.contains("location = /nginx-health"));
    }

    @Test
    void prometheusShouldScrapeCustomMetricsEndpoint() throws IOException {
        Map<String, Object> prometheus = loadYaml("deploy/prometheus/prometheus.yml");
        List<Map<String, Object>> scrapeConfigs = list(prometheus.get("scrape_configs"));
        assertEquals("railway-app", scrapeConfigs.get(0).get("job_name"));
        assertEquals("/internal/metrics", scrapeConfigs.get(0).get("metrics_path"));
    }

    @Test
    void grafanaDashboardAndDatasourceShouldBeValid() throws IOException {
        JsonNode dashboard = objectMapper.readTree(read("deploy/grafana/dashboards/railway-overview.json"));
        assertEquals("Railway 12306 应用概览", dashboard.path("title").asText());
        assertTrue(dashboard.path("panels").isArray());
        assertTrue(dashboard.path("panels").size() >= 4);

        Map<String, Object> datasource = loadYaml("deploy/grafana/provisioning/datasources/prometheus.yml");
        List<Map<String, Object>> datasources = list(datasource.get("datasources"));
        assertEquals("http://prometheus:9090", datasources.get(0).get("url"));
        assertEquals("prometheus", datasources.get(0).get("uid"));
    }

    private List<String> profilesOf(Map<String, Object> services, String name) {
        List<?> profiles = list(map(services.get(name)).get("profiles"));
        return profiles.stream().map(String::valueOf).toList();
    }

    private Map<String, Object> loadYaml(String path) throws IOException {
        return map(new Yaml().load(Files.newBufferedReader(Path.of(path))));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> map(Object value) {
        return (Map<String, Object>) value;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> list(Object value) {
        return (List<Map<String, Object>>) value;
    }

    private String read(String path) throws IOException {
        return Files.readString(Path.of(path));
    }
}
