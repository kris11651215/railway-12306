package com.railway.service;

import com.railway.common.ErrorCode;
import com.railway.dto.TrainVO;
import com.railway.entity.Station;
import com.railway.exception.BusinessException;
import com.railway.mapper.StationMapper;
import com.railway.mapper.TrainMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TrainServiceTest {

    @Mock
    private StationMapper stationMapper;

    @Mock
    private TrainMapper trainMapper;

    @InjectMocks
    private TrainService trainService;

    @Test
    void queryShouldFillChineseNamesAndDefaultSeatType() {
        when(stationMapper.selectByName("北京南")).thenReturn(station(1L, "北京南"));
        when(stationMapper.selectByName("上海虹桥")).thenReturn(station(5L, "上海虹桥"));
        TrainVO train = new TrainVO();
        train.setTrainNo("G1");
        train.setTrainType("G");
        train.setDirection("DOWN");
        when(trainMapper.queryTrains("北京南", "上海虹桥", LocalDate.of(2026, 4, 15), "二等座", null))
                .thenReturn(List.of(train));

        List<TrainVO> result = trainService.queryTrains("北京南", "上海虹桥",
                LocalDate.of(2026, 4, 15), null, null);

        assertEquals(1, result.size());
        assertEquals("高速动车组", train.getTrainTypeName());
        assertEquals("下行", train.getDirectionName());
        assertEquals("二等座", train.getSeatType());
    }

    @Test
    void sameStationShouldFailWithParamErrorWithoutQuerying() {
        BusinessException exception = assertThrows(BusinessException.class,
                () -> trainService.queryTrains("北京南", "北京南", LocalDate.of(2026, 4, 15), null, null));
        assertEquals(ErrorCode.PARAM_ERROR.getCode(), exception.getCode());
        verifyNoInteractions(stationMapper, trainMapper);
    }

    @Test
    void missingStationShouldFailWithStationNotFound() {
        when(stationMapper.selectByName("北京南")).thenReturn(station(1L, "北京南"));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> trainService.queryTrains("北京南", "火星站", LocalDate.of(2026, 4, 15), null, null));

        assertEquals(ErrorCode.STATION_NOT_FOUND.getCode(), exception.getCode());
        assertTrue(exception.getMessage().contains("火星站"));
    }

    private Station station(Long id, String name) {
        Station station = new Station();
        station.setId(id);
        station.setStationName(name);
        return station;
    }
}
