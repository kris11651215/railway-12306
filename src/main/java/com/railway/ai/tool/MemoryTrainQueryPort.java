package com.railway.ai.tool;

import com.railway.ai.StationAlias;
import com.railway.algorithm.SampleTimetable;
import com.railway.algorithm.TrainSchedule;
import com.railway.algorithm.TrainStop;
import com.railway.dto.TrainVO;
import com.railway.entity.Direction;
import com.railway.entity.TrainType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

public class MemoryTrainQueryPort implements TrainQueryPort {

    private final Map<String, BigDecimal> prices = SampleTimetable.segmentPrices();
    private final Map<String, Integer> remaining = SampleTimetable.segmentRemaining();

    @Override
    public List<TrainVO> query(String from, String to, LocalDate travelDate, String seatType) {
        String fromName = StationAlias.resolve(from);
        String toName = StationAlias.resolve(to);
        List<TrainVO> result = new ArrayList<>();
        for (TrainSchedule schedule : SampleTimetable.schedules()) {
            TrainStop fromStop = findStop(schedule, fromName);
            TrainStop toStop = findStop(schedule, toName);
            if (fromStop == null || toStop == null || fromStop.getStationOrder() >= toStop.getStationOrder()) {
                continue;
            }
            result.add(toTrainVO(schedule, fromStop, toStop, fromName, toName, seatType));
        }
        result.sort(Comparator.comparing(TrainVO::getDepartureTime));
        return result;
    }

    private TrainVO toTrainVO(TrainSchedule schedule, TrainStop fromStop, TrainStop toStop,
                              String fromName, String toName, String seatType) {
        TrainVO vo = new TrainVO();
        vo.setTrainNo(schedule.getTrainNo());
        vo.setTrainType(schedule.getTrainType());
        vo.setTrainTypeName(TrainType.valueOf(schedule.getTrainType()).getChineseName());
        boolean up = isUp(schedule.getTrainNo());
        vo.setDirection(up ? Direction.UP.name() : Direction.DOWN.name());
        vo.setDirectionName(up ? Direction.UP.getChineseName() : Direction.DOWN.getChineseName());
        vo.setBureau(SampleTimetable.bureauOf(schedule.getTrainNo()));
        vo.setFromStation(fromName);
        vo.setToStation(toName);
        vo.setDepartureTime(fromStop.getDepartureTime());
        vo.setArrivalTime(toStop.getArrivalTime());
        vo.setDurationMinutes(toStop.arrivalMinuteOfDay() - fromStop.departureMinuteOfDay());
        vo.setMileage(toStop.getMileageFromStart() - fromStop.getMileageFromStart());
        vo.setTotalPrice(sumPrice(schedule.getTrainNo(), fromStop.getStationOrder(), toStop.getStationOrder()));
        vo.setRemainingCount(minRemaining(schedule.getTrainNo(), fromStop.getStationOrder(), toStop.getStationOrder()));
        vo.setSeatType(seatType == null || seatType.isBlank()
                ? SampleTimetable.seatTypeOf(schedule.getTrainType()) : seatType);
        return vo;
    }

    private BigDecimal sumPrice(String trainNo, int fromOrder, int toOrder) {
        BigDecimal total = BigDecimal.ZERO;
        for (int order = fromOrder; order < toOrder; order++) {
            total = total.add(prices.getOrDefault(SampleTimetable.segmentKey(trainNo, order), BigDecimal.ZERO));
        }
        return total;
    }

    private int minRemaining(String trainNo, int fromOrder, int toOrder) {
        int min = Integer.MAX_VALUE;
        for (int order = fromOrder; order < toOrder; order++) {
            min = Math.min(min, remaining.getOrDefault(SampleTimetable.segmentKey(trainNo, order), 120));
        }
        return min == Integer.MAX_VALUE ? 120 : min;
    }

    private boolean isUp(String trainNo) {
        String digits = trainNo.replaceAll("\\D", "");
        return !digits.isEmpty() && Integer.parseInt(digits) % 2 == 0;
    }

    private TrainStop findStop(TrainSchedule schedule, String stationName) {
        for (TrainStop stop : schedule.getStops()) {
            if (stop.getStationName().equals(stationName)) {
                return stop;
            }
        }
        return null;
    }
}
