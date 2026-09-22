package com.railway.algorithm;

import com.railway.config.RouteProperties;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

public class MemoryTransferGraphProvider implements TransferGraphProvider {

    private final TimeExpandedGraph graph;

    public MemoryTransferGraphProvider(RouteProperties properties) {
        TimeExpandedGraph.Builder builder = TimeExpandedGraph.builder()
                .minTransferMinutes(properties.getMinTransferMinutes())
                .maxWaitMinutes(properties.getMaxWaitMinutes())
                .schedules(SampleTimetable.schedules());
        for (Map.Entry<String, BigDecimal> entry : SampleTimetable.segmentPrices().entrySet()) {
            String[] parts = entry.getKey().split("#");
            builder.segmentPrice(parts[0], Integer.parseInt(parts[1]), entry.getValue());
        }
        this.graph = builder.build();
    }

    @Override
    public TimeExpandedGraph graph(LocalDate travelDate) {
        return graph;
    }
}
