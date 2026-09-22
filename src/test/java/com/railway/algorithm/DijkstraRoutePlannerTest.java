package com.railway.algorithm;

import com.railway.dto.TransferPlanVO;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class DijkstraRoutePlannerTest {

    private final DijkstraRoutePlanner planner = new DijkstraRoutePlanner();
    private final RouteOptions options = new RouteOptions(2, 1440);

    @Test
    void fastestShouldPreferShortestDuration() {
        TransferPlanVO plan = planner.plan(demoGraph(), "甲城", "乙城", RouteStrategy.FASTEST, options);
        assertEquals(1, plan.getLegs().size());
        assertEquals("D1", plan.getLegs().get(0).getTrainNo());
        assertEquals(120, plan.getTotalDurationMinutes());
        assertEquals(new BigDecimal("800.00"), plan.getTotalPrice());
    }

    @Test
    void cheapestShouldPreferLowestPrice() {
        TransferPlanVO plan = planner.plan(demoGraph(), "甲城", "乙城", RouteStrategy.CHEAPEST, options);
        assertEquals(2, plan.getLegs().size());
        assertEquals("T1", plan.getLegs().get(0).getTrainNo());
        assertEquals("T2", plan.getLegs().get(1).getTrainNo());
        assertEquals(1, plan.getTransferCount());
        assertEquals(List.of("丙城"), plan.getTransferStations());
        assertEquals(30, plan.getLegs().get(1).getWaitBeforeMinutes());
        assertEquals(180, plan.getTotalDurationMinutes());
        assertEquals(new BigDecimal("100.00"), plan.getTotalPrice());
    }

    @Test
    void maxTransfersZeroShouldForceDirectTrain() {
        TransferPlanVO plan = planner.plan(demoGraph(), "甲城", "乙城", RouteStrategy.CHEAPEST,
                new RouteOptions(0, 1440));
        assertEquals("D1", plan.getLegs().get(0).getTrainNo());
    }

    @Test
    void unreachableDestinationShouldReturnNull() {
        assertNull(planner.plan(demoGraph(), "乙城", "甲城", RouteStrategy.FASTEST, options));
    }

    static TimeExpandedGraph demoGraph() {
        return TimeExpandedGraph.builder()
                .minTransferMinutes(10)
                .maxWaitMinutes(240)
                .schedule(new TrainSchedule("D1", "G", List.of(
                        stop("甲城", 1, null, "08:00", 0),
                        stop("乙城", 2, "10:00", null, 600))))
                .schedule(new TrainSchedule("T1", "G", List.of(
                        stop("甲城", 1, null, "08:00", 0),
                        stop("丙城", 2, "09:00", null, 300))))
                .schedule(new TrainSchedule("T2", "G", List.of(
                        stop("丙城", 1, null, "09:30", 0),
                        stop("乙城", 2, "11:00", null, 300))))
                .segmentPrice("D1", 1, new BigDecimal("800.00"))
                .segmentPrice("T1", 1, new BigDecimal("50.00"))
                .segmentPrice("T2", 1, new BigDecimal("50.00"))
                .build();
    }

    private static TrainStop stop(String station, int order, String arrival, String departure, int mileage) {
        return new TrainStop(station, order,
                arrival == null ? null : LocalTime.parse(arrival),
                departure == null ? null : LocalTime.parse(departure),
                0, mileage);
    }
}
