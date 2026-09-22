package com.railway.algorithm;

import com.railway.dto.TransferPlanVO;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

public abstract class AbstractRoutePlanner implements RoutePlanner {

    @Override
    public TransferPlanVO plan(TimeExpandedGraph graph, String fromStation, String toStation,
                               RouteStrategy strategy, RouteOptions options) {
        List<TimeExpandedGraph.EventNode> origins = graph.departureNodes(fromStation);
        if (origins.isEmpty() || graph.arrivalNodes(toStation).isEmpty()) {
            return null;
        }
        prepare(graph, toStation, strategy);
        List<TimeExpandedGraph.EventNode> nodes = graph.getNodes();
        Map<Integer, State> best = new HashMap<>();
        PriorityQueue<State> queue = new PriorityQueue<>(Comparator
                .comparingLong(State::priority)
                .thenComparingInt(state -> state.elapsed)
                .thenComparingInt(state -> state.transfers));
        for (TimeExpandedGraph.EventNode origin : origins) {
            State state = new State(origin.getId(), 0, 0, 0, 0, null, null, heuristic(origin, toStation, strategy));
            best.put(origin.getId(), state);
            queue.add(state);
        }
        State goal = null;
        while (!queue.isEmpty()) {
            State current = queue.poll();
            if (current.settled) {
                continue;
            }
            current.settled = true;
            TimeExpandedGraph.EventNode node = nodes.get(current.nodeId);
            if (!node.isDeparture() && node.getStationName().equals(toStation)) {
                goal = current;
                break;
            }
            for (TimeExpandedGraph.GraphEdge edge : graph.getAdjacency().getOrDefault(node.getId(), List.of())) {
                int transfers = current.transfers + (edge.isRide() ? 0 : 1);
                if (transfers > options.getMaxTransfers()) {
                    continue;
                }
                int elapsed = current.elapsed + edge.getMinutes();
                if (elapsed > options.getMaxDurationMinutes()) {
                    continue;
                }
                State next = new State(edge.getTo().getId(),
                        current.primary + primaryWeight(edge, strategy),
                        elapsed,
                        current.priceCents + edge.getPriceCents(),
                        transfers,
                        current,
                        edge,
                        heuristic(edge.getTo(), toStation, strategy));
                State known = best.get(next.nodeId);
                if (known == null || isBetter(next, known)) {
                    best.put(next.nodeId, next);
                    queue.add(next);
                }
            }
        }
        return goal == null ? null : toPlan(graph, goal, fromStation, toStation, strategy);
    }

    protected void prepare(TimeExpandedGraph graph, String toStation, RouteStrategy strategy) {
    }

    protected long heuristic(TimeExpandedGraph.EventNode node, String toStation, RouteStrategy strategy) {
        return 0;
    }

    private long primaryWeight(TimeExpandedGraph.GraphEdge edge, RouteStrategy strategy) {
        return strategy == RouteStrategy.FASTEST ? edge.getMinutes() : edge.getPriceCents();
    }

    private boolean isBetter(State candidate, State known) {
        if (candidate.primary != known.primary) {
            return candidate.primary < known.primary;
        }
        if (candidate.elapsed != known.elapsed) {
            return candidate.elapsed < known.elapsed;
        }
        return candidate.transfers < known.transfers;
    }

    private TransferPlanVO toPlan(TimeExpandedGraph graph, State goal, String fromStation,
                                  String toStation, RouteStrategy strategy) {
        List<TimeExpandedGraph.GraphEdge> path = new ArrayList<>();
        State cursor = goal;
        while (cursor.previous != null) {
            path.add(0, cursor.via);
            cursor = cursor.previous;
        }
        List<TimeExpandedGraph.GraphEdge> rideEdges = new ArrayList<>();
        List<Integer> waits = new ArrayList<>();
        int pendingWait = 0;
        for (TimeExpandedGraph.GraphEdge edge : path) {
            if (edge.isRide()) {
                rideEdges.add(edge);
                waits.add(pendingWait);
                pendingWait = 0;
            } else {
                pendingWait = edge.getMinutes();
            }
        }

        List<TransferPlanVO.Leg> legs = new ArrayList<>();
        List<String> transferStations = new ArrayList<>();
        for (int index = 0; index < rideEdges.size(); index++) {
            TimeExpandedGraph.GraphEdge edge = rideEdges.get(index);
            TransferPlanVO.Leg leg = new TransferPlanVO.Leg();
            leg.setTrainNo(edge.getFrom().getTrainNo());
            leg.setTrainType(edge.getFrom().getTrainType());
            leg.setFromStation(edge.getFromStation());
            leg.setToStation(edge.getToStation());
            leg.setDepartureTime(formatMinute(edge.getDepartureMinute()));
            leg.setArrivalTime(formatMinute(edge.getArrivalMinute()));
            leg.setDepartureDayOffset(edge.getDepartureMinute() / 1440);
            leg.setArrivalDayOffset(edge.getArrivalMinute() / 1440);
            leg.setDurationMinutes(edge.getMinutes());
            leg.setPrice(edge.getPrice());
            leg.setWaitBeforeMinutes(waits.get(index));
            legs.add(leg);
            if (index < rideEdges.size() - 1) {
                transferStations.add(edge.getToStation());
            }
        }

        TransferPlanVO plan = new TransferPlanVO();
        plan.setFromStation(fromStation);
        plan.setToStation(toStation);
        plan.setStrategy(strategy.getCode());
        plan.setStrategyName(strategy.getChineseName());
        plan.setTransferCount(Math.max(rideEdges.size() - 1, 0));
        plan.setTransferStations(transferStations);
        plan.setTotalDurationMinutes(goal.elapsed);
        plan.setTotalPrice(BigDecimal.valueOf(goal.priceCents, 2));
        plan.setLegs(legs);
        return plan;
    }

    private String formatMinute(int absoluteMinute) {
        int minuteOfDay = Math.floorMod(absoluteMinute, 1440);
        return String.format("%02d:%02d", minuteOfDay / 60, minuteOfDay % 60);
    }

    private static class State {

        private final int nodeId;
        private final long primary;
        private final int elapsed;
        private final long priceCents;
        private final int transfers;
        private final State previous;
        private final TimeExpandedGraph.GraphEdge via;
        private final long heuristic;
        private boolean settled;

        State(int nodeId, long primary, int elapsed, long priceCents, int transfers,
              State previous, TimeExpandedGraph.GraphEdge via, long heuristic) {
            this.nodeId = nodeId;
            this.primary = primary;
            this.elapsed = elapsed;
            this.priceCents = priceCents;
            this.transfers = transfers;
            this.previous = previous;
            this.via = via;
            this.heuristic = heuristic;
        }

        long priority() {
            return primary + heuristic;
        }
    }
}
