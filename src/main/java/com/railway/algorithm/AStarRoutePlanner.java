package com.railway.algorithm;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

@Component
public class AStarRoutePlanner extends AbstractRoutePlanner {

    private static final double MAX_SPEED_KM_PER_HOUR = 350.0;

    private Map<String, Double> optimisticMinutes = Map.of();

    @Override
    public String algorithmName() {
        return "astar";
    }

    @Override
    protected void prepare(TimeExpandedGraph graph, String toStation, RouteStrategy strategy) {
        this.optimisticMinutes = strategy == RouteStrategy.FASTEST
                ? buildOptimisticMinutes(graph, toStation)
                : Map.of();
    }

    @Override
    protected long heuristic(TimeExpandedGraph.EventNode node, String toStation, RouteStrategy strategy) {
        if (strategy != RouteStrategy.FASTEST) {
            return 0;
        }
        Double minutes = optimisticMinutes.get(node.getStationName());
        return minutes == null ? 0 : minutes.longValue();
    }

    private Map<String, Double> buildOptimisticMinutes(TimeExpandedGraph graph, String toStation) {
        List<TimeExpandedGraph.GraphEdge> rideEdges = new ArrayList<>();
        for (List<TimeExpandedGraph.GraphEdge> edges : graph.getAdjacency().values()) {
            for (TimeExpandedGraph.GraphEdge edge : edges) {
                if (edge.isRide() && edge.getMileageDelta() > 0) {
                    rideEdges.add(edge);
                }
            }
        }
        Map<String, List<TimeExpandedGraph.GraphEdge>> reversed = new HashMap<>();
        for (TimeExpandedGraph.GraphEdge edge : rideEdges) {
            reversed.computeIfAbsent(edge.getToStation(), key -> new ArrayList<>()).add(edge);
        }
        Map<String, Double> kilometers = new HashMap<>();
        kilometers.put(toStation, 0.0);
        PriorityQueue<StationDistance> queue = new PriorityQueue<>(Comparator.comparingDouble(StationDistance::distance));
        queue.add(new StationDistance(toStation, 0.0));
        while (!queue.isEmpty()) {
            StationDistance current = queue.poll();
            if (current.distance > kilometers.getOrDefault(current.station, Double.MAX_VALUE) + 1e-9) {
                continue;
            }
            for (TimeExpandedGraph.GraphEdge edge : reversed.getOrDefault(current.station, List.of())) {
                double candidate = current.distance + edge.getMileageDelta();
                if (candidate < kilometers.getOrDefault(edge.getFromStation(), Double.MAX_VALUE)) {
                    kilometers.put(edge.getFromStation(), candidate);
                    queue.add(new StationDistance(edge.getFromStation(), candidate));
                }
            }
        }
        Map<String, Double> result = new HashMap<>();
        for (Map.Entry<String, Double> entry : kilometers.entrySet()) {
            result.put(entry.getKey(), Math.floor(entry.getValue() / MAX_SPEED_KM_PER_HOUR * 60));
        }
        return result;
    }

    private static class StationDistance {

        private final String station;
        private final double distance;

        StationDistance(String station, double distance) {
            this.station = station;
            this.distance = distance;
        }

        String station() {
            return station;
        }

        double distance() {
            return distance;
        }
    }
}
