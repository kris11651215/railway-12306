package com.railway.controller;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryUsage;
import java.util.List;

@RestController
@RequestMapping("/internal")
public class MetricsController {

    @GetMapping(value = "/metrics", produces = MediaType.TEXT_PLAIN_VALUE)
    public String metrics() {
        MemoryUsage heap = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage();
        MemoryUsage nonHeap = ManagementFactory.getMemoryMXBean().getNonHeapMemoryUsage();
        int threadCount = ManagementFactory.getThreadMXBean().getThreadCount();
        long uptimeSeconds = ManagementFactory.getRuntimeMXBean().getUptime() / 1000;
        double systemLoad = ManagementFactory.getOperatingSystemMXBean().getSystemLoadAverage();
        int processors = ManagementFactory.getOperatingSystemMXBean().getAvailableProcessors();

        long gcCount = 0;
        long gcTimeMillis = 0;
        List<GarbageCollectorMXBean> gcBeans = ManagementFactory.getGarbageCollectorMXBeans();
        for (GarbageCollectorMXBean gcBean : gcBeans) {
            if (gcBean.getCollectionCount() > 0) {
                gcCount += gcBean.getCollectionCount();
            }
            if (gcBean.getCollectionTime() > 0) {
                gcTimeMillis += gcBean.getCollectionTime();
            }
        }

        StringBuilder builder = new StringBuilder();
        metric(builder, "railway_up", "Service alive marker", "gauge", 1);
        metric(builder, "railway_jvm_heap_used_bytes", "JVM heap used bytes", "gauge", heap.getUsed());
        metric(builder, "railway_jvm_heap_committed_bytes", "JVM heap committed bytes", "gauge", heap.getCommitted());
        metric(builder, "railway_jvm_heap_max_bytes", "JVM heap max bytes", "gauge", heap.getMax());
        metric(builder, "railway_jvm_nonheap_used_bytes", "JVM non-heap used bytes", "gauge", nonHeap.getUsed());
        metric(builder, "railway_jvm_threads", "Live JVM thread count", "gauge", threadCount);
        metric(builder, "railway_jvm_uptime_seconds", "JVM uptime seconds", "gauge", uptimeSeconds);
        metric(builder, "railway_jvm_gc_collections_total", "JVM GC collections total", "counter", gcCount);
        metric(builder, "railway_jvm_gc_time_millis_total", "JVM GC time millis total", "counter", gcTimeMillis);
        metric(builder, "railway_system_load_average", "System load average one minute", "gauge", systemLoad);
        metric(builder, "railway_system_processors", "Available processors", "gauge", processors);
        return builder.toString();
    }

    private void metric(StringBuilder builder, String name, String help, String type, Number value) {
        builder.append("# HELP ").append(name).append(' ').append(help).append('\n');
        builder.append("# TYPE ").append(name).append(' ').append(type).append('\n');
        builder.append(name).append(' ').append(value).append('\n');
    }
}
