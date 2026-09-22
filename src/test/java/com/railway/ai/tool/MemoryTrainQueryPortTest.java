package com.railway.ai.tool;

import com.railway.dto.TrainVO;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MemoryTrainQueryPortTest {

    private final MemoryTrainQueryPort port = new MemoryTrainQueryPort();

    @Test
    void aliasShouldResolveAndReturnG1Details() {
        List<TrainVO> trains = port.query("北京", "上海", LocalDate.of(2026, 4, 15), null);
        assertEquals(1, trains.size());
        TrainVO train = trains.get(0);
        assertEquals("G1", train.getTrainNo());
        assertEquals("北京南", train.getFromStation());
        assertEquals("上海虹桥", train.getToStation());
        assertEquals(268, train.getDurationMinutes());
        assertEquals(1318, train.getMileage());
        assertEquals(3, train.getRemainingCount());
        assertEquals(0, train.getTotalPrice().compareTo(new java.math.BigDecimal("716.50")));
        assertEquals("二等座", train.getSeatType());
        assertEquals("DOWN", train.getDirection());
        assertEquals("高速动车组", train.getTrainTypeName());
    }

    @Test
    void reverseDirectionTrainShouldBeFound() {
        List<TrainVO> trains = port.query("深圳北", "广州南", LocalDate.of(2026, 4, 15), null);
        assertEquals(1, trains.size());
        assertEquals("G6002", trains.get(0).getTrainNo());
        assertEquals("UP", trains.get(0).getDirection());
    }

    @Test
    void noDirectTrainShouldReturnEmptyList() {
        List<TrainVO> trains = port.query("广州南", "北京南", LocalDate.of(2026, 4, 15), null);
        assertTrue(trains.isEmpty());
    }

    @Test
    void crossDayK599ShouldReturnPositiveDuration() {
        List<TrainVO> trains = port.query("北京西", "广州南", LocalDate.of(2026, 4, 15), null);
        assertEquals(1, trains.size());
        assertEquals("K599", trains.get(0).getTrainNo());
        assertNotNull(trains.get(0).getDurationMinutes());
        assertTrue(trains.get(0).getDurationMinutes() > 900);
        assertEquals("硬座", trains.get(0).getSeatType());
    }
}
