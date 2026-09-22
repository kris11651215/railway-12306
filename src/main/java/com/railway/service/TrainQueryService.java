package com.railway.service;

import com.railway.entity.Station;
import com.railway.entity.Train;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public class TrainQueryService {

    private final List<Train> trains;
    private final Map<String, Station> stationIndex;

    public TrainQueryService(List<Station> stations, List<Train> trains) {
        this.trains = new ArrayList<>(trains);
        this.trains.sort(Comparator.comparing(Train::getDepartureTime));
        this.stationIndex = new LinkedHashMap<>();
        for (Station station : stations) {
            this.stationIndex.put(station.getStationName(), station);
        }
    }

    public Optional<Station> findStation(String stationName) {
        return Optional.ofNullable(stationIndex.get(stationName));
    }

    public Set<String> stationNames() {
        return Collections.unmodifiableSet(stationIndex.keySet());
    }

    public List<Train> query(String from, String to) {
        if (from == null || from.isBlank() || to == null || to.isBlank()) {
            throw new IllegalArgumentException("出发站与到达站都不能为空");
        }
        if (from.equals(to)) {
            throw new IllegalArgumentException("出发站与到达站不能相同");
        }
        List<Train> matched = new ArrayList<>();
        for (Train train : trains) {
            int fromIndex = train.getStopStations().indexOf(from);
            int toIndex = train.getStopStations().indexOf(to);
            if (fromIndex >= 0 && toIndex >= 0 && fromIndex < toIndex) {
                matched.add(train);
            }
        }
        return matched;
    }
}
