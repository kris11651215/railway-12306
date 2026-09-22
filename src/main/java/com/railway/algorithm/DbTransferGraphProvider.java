package com.railway.algorithm;

import com.railway.config.RouteProperties;
import com.railway.dto.SegmentPriceRow;
import com.railway.dto.TrainStopRow;
import com.railway.mapper.TrainMapper;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class DbTransferGraphProvider implements TransferGraphProvider {

    private static final String DEFAULT_SEAT_TYPE = "二等座";
    private static final String FALLBACK_SEAT_TYPE = "硬座";

    private final TrainMapper trainMapper;
    private final RouteProperties properties;

    public DbTransferGraphProvider(TrainMapper trainMapper, RouteProperties properties) {
        this.trainMapper = trainMapper;
        this.properties = properties;
    }

    @Override
    public TimeExpandedGraph graph(LocalDate travelDate) {
        TimeExpandedGraph.Builder builder = TimeExpandedGraph.builder()
                .minTransferMinutes(properties.getMinTransferMinutes())
                .maxWaitMinutes(properties.getMaxWaitMinutes())
                .schedules(loadSchedules());
        Map<String, Map<Integer, BigDecimal>> prices = loadPrices(travelDate);
        for (Map.Entry<String, Map<Integer, BigDecimal>> trainEntry : prices.entrySet()) {
            for (Map.Entry<Integer, BigDecimal> segment : trainEntry.getValue().entrySet()) {
                builder.segmentPrice(trainEntry.getKey(), segment.getKey(), segment.getValue());
            }
        }
        return builder.build();
    }

    private List<TrainSchedule> loadSchedules() {
        Map<String, List<TrainStopRow>> grouped = new LinkedHashMap<>();
        for (TrainStopRow row : trainMapper.selectAllStops()) {
            grouped.computeIfAbsent(row.getTrainNo(), key -> new ArrayList<>()).add(row);
        }
        List<TrainSchedule> schedules = new ArrayList<>();
        for (Map.Entry<String, List<TrainStopRow>> entry : grouped.entrySet()) {
            List<TrainStop> stops = new ArrayList<>();
            String trainType = entry.getValue().get(0).getTrainType();
            for (TrainStopRow row : entry.getValue()) {
                stops.add(new TrainStop(row.getStationName(), row.getStationOrder(),
                        row.getArrivalTime(), row.getDepartureTime(),
                        row.getDayOffset() == null ? 0 : row.getDayOffset(),
                        row.getMileageFromStart() == null ? 0 : row.getMileageFromStart()));
            }
            schedules.add(new TrainSchedule(entry.getKey(), trainType, stops));
        }
        return schedules;
    }

    private Map<String, Map<Integer, BigDecimal>> loadPrices(LocalDate travelDate) {
        Map<String, Map<Integer, BigDecimal>> prices = new LinkedHashMap<>();
        mergePrices(prices, trainMapper.selectSegmentPrices(travelDate, DEFAULT_SEAT_TYPE));
        mergeFallbackPrices(prices, trainMapper.selectSegmentPrices(travelDate, FALLBACK_SEAT_TYPE));
        return prices;
    }

    private void mergePrices(Map<String, Map<Integer, BigDecimal>> prices, List<SegmentPriceRow> rows) {
        for (SegmentPriceRow row : rows) {
            prices.computeIfAbsent(row.getTrainNo(), key -> new LinkedHashMap<>())
                    .put(row.getFromStationOrder(), row.getPrice());
        }
    }

    private void mergeFallbackPrices(Map<String, Map<Integer, BigDecimal>> prices, List<SegmentPriceRow> rows) {
        for (SegmentPriceRow row : rows) {
            prices.computeIfAbsent(row.getTrainNo(), key -> new LinkedHashMap<>())
                    .putIfAbsent(row.getFromStationOrder(), row.getPrice());
        }
    }
}
