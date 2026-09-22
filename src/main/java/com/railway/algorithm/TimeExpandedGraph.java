package com.railway.algorithm;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TimeExpandedGraph {

    public static final int MAX_WAIT_MINUTES = 240;
    public static final int MIN_TRANSFER_MINUTES = 10;

    private final List<EventNode> nodes;
    private final Map<Integer, List<GraphEdge>> adjacency;
    private final Map<String, List<EventNode>> departureNodesByStation;
    private final Map<String, List<EventNode>> arrivalNodesByStation;

    private TimeExpandedGraph(List<EventNode> nodes, Map<Integer, List<GraphEdge>> adjacency,
                              Map<String, List<EventNode>> departureNodesByStation,
                              Map<String, List<EventNode>> arrivalNodesByStation) {
        this.nodes = List.copyOf(nodes);
        this.adjacency = adjacency;
        this.departureNodesByStation = departureNodesByStation;
        this.arrivalNodesByStation = arrivalNodesByStation;
    }

    public List<EventNode> getNodes() {
        return nodes;
    }

    public Map<Integer, List<GraphEdge>> getAdjacency() {
        return adjacency;
    }

    public List<EventNode> departureNodes(String stationName) {
        return departureNodesByStation.getOrDefault(stationName, List.of());
    }

    public List<EventNode> arrivalNodes(String stationName) {
        return arrivalNodesByStation.getOrDefault(stationName, List.of());
    }

    public boolean containsStation(String stationName) {
        return departureNodesByStation.containsKey(stationName) || arrivalNodesByStation.containsKey(stationName);
    }

    public static class EventNode {

        private final int id;
        private final String stationName;
        private final String trainNo;
        private final String trainType;
        private final int stationOrder;
        private final int absoluteMinute;
        private final boolean departure;

        EventNode(int id, String stationName, String trainNo, String trainType,
                  int stationOrder, int absoluteMinute, boolean departure) {
            this.id = id;
            this.stationName = stationName;
            this.trainNo = trainNo;
            this.trainType = trainType;
            this.stationOrder = stationOrder;
            this.absoluteMinute = absoluteMinute;
            this.departure = departure;
        }

        public int getId() {
            return id;
        }

        public String getStationName() {
            return stationName;
        }

        public String getTrainNo() {
            return trainNo;
        }

        public String getTrainType() {
            return trainType;
        }

        public int getStationOrder() {
            return stationOrder;
        }

        public int getAbsoluteMinute() {
            return absoluteMinute;
        }

        public boolean isDeparture() {
            return departure;
        }
    }

    public static class GraphEdge {

        private final EventNode from;
        private final EventNode to;
        private final boolean ride;
        private final int minutes;
        private final long priceCents;
        private final int mileageDelta;
        private final String fromStation;
        private final String toStation;
        private final int departureMinute;
        private final int arrivalMinute;

        GraphEdge(EventNode from, EventNode to, boolean ride, int minutes, long priceCents,
                  int mileageDelta, String fromStation, String toStation,
                  int departureMinute, int arrivalMinute) {
            this.from = from;
            this.to = to;
            this.ride = ride;
            this.minutes = minutes;
            this.priceCents = priceCents;
            this.mileageDelta = mileageDelta;
            this.fromStation = fromStation;
            this.toStation = toStation;
            this.departureMinute = departureMinute;
            this.arrivalMinute = arrivalMinute;
        }

        public EventNode getFrom() {
            return from;
        }

        public EventNode getTo() {
            return to;
        }

        public boolean isRide() {
            return ride;
        }

        public int getMinutes() {
            return minutes;
        }

        public long getPriceCents() {
            return priceCents;
        }

        public BigDecimal getPrice() {
            return BigDecimal.valueOf(priceCents, 2);
        }

        public int getMileageDelta() {
            return mileageDelta;
        }

        public String getFromStation() {
            return fromStation;
        }

        public String getToStation() {
            return toStation;
        }

        public int getDepartureMinute() {
            return departureMinute;
        }

        public int getArrivalMinute() {
            return arrivalMinute;
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private final List<TrainSchedule> schedules = new ArrayList<>();
        private final Map<String, Long> segmentPrices = new HashMap<>();
        private int minTransferMinutes = MIN_TRANSFER_MINUTES;
        private int maxWaitMinutes = MAX_WAIT_MINUTES;
        private long noPriceFallbackCentsPerKm = 45;

        public Builder schedules(List<TrainSchedule> schedules) {
            this.schedules.addAll(schedules);
            return this;
        }

        public Builder schedule(TrainSchedule schedule) {
            this.schedules.add(schedule);
            return this;
        }

        public Builder segmentPrice(String trainNo, int fromStationOrder, BigDecimal price) {
            this.segmentPrices.put(priceKey(trainNo, fromStationOrder), price.movePointRight(2).longValue());
            return this;
        }

        public Builder minTransferMinutes(int minTransferMinutes) {
            this.minTransferMinutes = minTransferMinutes;
            return this;
        }

        public Builder maxWaitMinutes(int maxWaitMinutes) {
            this.maxWaitMinutes = maxWaitMinutes;
            return this;
        }

        public TimeExpandedGraph build() {
            List<EventNode> nodes = new ArrayList<>();
            Map<Integer, List<GraphEdge>> adjacency = new HashMap<>();
            Map<String, List<EventNode>> departures = new HashMap<>();
            Map<String, List<EventNode>> arrivals = new HashMap<>();
            Map<String, List<EventNode>> trainDepartures = new HashMap<>();
            Map<String, List<EventNode>> trainArrivals = new HashMap<>();

            int nextId = 0;
            for (TrainSchedule schedule : schedules) {
                for (TrainStop stop : schedule.getStops()) {
                    if (stop.hasDeparture()) {
                        EventNode node = new EventNode(nextId++, stop.getStationName(), schedule.getTrainNo(),
                                schedule.getTrainType(), stop.getStationOrder(), stop.departureMinuteOfDay(), true);
                        nodes.add(node);
                        adjacency.put(node.getId(), new ArrayList<>());
                        departures.computeIfAbsent(node.getStationName(), key -> new ArrayList<>()).add(node);
                        trainDepartures.computeIfAbsent(schedule.getTrainNo(), key -> new ArrayList<>()).add(node);
                    }
                    if (stop.hasArrival()) {
                        EventNode node = new EventNode(nextId++, stop.getStationName(), schedule.getTrainNo(),
                                schedule.getTrainType(), stop.getStationOrder(), stop.arrivalMinuteOfDay(), false);
                        nodes.add(node);
                        adjacency.put(node.getId(), new ArrayList<>());
                        arrivals.computeIfAbsent(node.getStationName(), key -> new ArrayList<>()).add(node);
                        trainArrivals.computeIfAbsent(schedule.getTrainNo(), key -> new ArrayList<>()).add(node);
                    }
                }
            }

            for (TrainSchedule schedule : schedules) {
                List<TrainStop> stops = schedule.getStops();
                List<EventNode> trainDeps = trainDepartures.getOrDefault(schedule.getTrainNo(), List.of());
                List<EventNode> trainArrs = trainArrivals.getOrDefault(schedule.getTrainNo(), List.of());
                for (EventNode departure : trainDeps) {
                    for (EventNode arrival : trainArrs) {
                        if (arrival.getStationOrder() <= departure.getStationOrder()) {
                            continue;
                        }
                        int minutes = arrival.getAbsoluteMinute() - departure.getAbsoluteMinute();
                        if (minutes <= 0) {
                            continue;
                        }
                        long priceCents = 0;
                        int mileage = mileageBetween(stops, departure.getStationOrder(), arrival.getStationOrder());
                        boolean priced = true;
                        for (int order = departure.getStationOrder(); order < arrival.getStationOrder(); order++) {
                            Long segmentPrice = segmentPrices.get(priceKey(schedule.getTrainNo(), order));
                            if (segmentPrice == null) {
                                priced = false;
                                break;
                            }
                            priceCents += segmentPrice;
                        }
                        if (!priced) {
                            priceCents = Math.round(mileage * noPriceFallbackCentsPerKm);
                        }
                        adjacency.get(departure.getId()).add(new GraphEdge(departure, arrival, true, minutes, priceCents,
                                mileage, departure.getStationName(), arrival.getStationName(),
                                departure.getAbsoluteMinute(), arrival.getAbsoluteMinute()));
                    }
                }
            }

            for (Map.Entry<String, List<EventNode>> entry : arrivals.entrySet()) {
                List<EventNode> stationArrivals = entry.getValue();
                List<EventNode> stationDepartures = departures.getOrDefault(entry.getKey(), List.of());
                for (EventNode arrival : stationArrivals) {
                    for (EventNode departure : stationDepartures) {
                        if (arrival.getTrainNo().equals(departure.getTrainNo())) {
                            continue;
                        }
                        int wait = departure.getAbsoluteMinute() - arrival.getAbsoluteMinute();
                        if (wait < minTransferMinutes || wait > maxWaitMinutes) {
                            continue;
                        }
                        adjacency.get(arrival.getId()).add(new GraphEdge(arrival, departure, false, wait, 0,
                                0, arrival.getStationName(), departure.getStationName(),
                                arrival.getAbsoluteMinute(), departure.getAbsoluteMinute()));
                    }
                }
            }

            return new TimeExpandedGraph(nodes, adjacency, departures, arrivals);
        }

        private static int mileageBetween(List<TrainStop> stops, int fromOrder, int toOrder) {
            int fromMileage = 0;
            int toMileage = 0;
            for (TrainStop stop : stops) {
                if (stop.getStationOrder() == fromOrder) {
                    fromMileage = stop.getMileageFromStart();
                }
                if (stop.getStationOrder() == toOrder) {
                    toMileage = stop.getMileageFromStart();
                }
            }
            return Math.max(toMileage - fromMileage, 0);
        }

        private static String priceKey(String trainNo, int fromStationOrder) {
            return trainNo + "#" + fromStationOrder;
        }
    }
}
