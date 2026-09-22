package com.railway.service;

import com.railway.common.ErrorCode;
import com.railway.dto.OrderCreateRequest;
import com.railway.dto.OrderCreateVO;
import com.railway.entity.SeatInventory;
import com.railway.entity.Station;
import com.railway.entity.TicketOrder;
import com.railway.entity.Train;
import com.railway.exception.BusinessException;
import com.railway.mapper.SeatInventoryMapper;
import com.railway.mapper.StationMapper;
import com.railway.mapper.TicketOrderMapper;
import com.railway.mapper.TrainMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    private static final LocalDate TRAVEL_DATE = LocalDate.of(2026, 4, 15);

    @Mock
    private StationMapper stationMapper;

    @Mock
    private TrainMapper trainMapper;

    @Mock
    private SeatInventoryMapper seatInventoryMapper;

    @Mock
    private TicketOrderMapper ticketOrderMapper;

    @InjectMocks
    private OrderService orderService;

    @Test
    void createOrderShouldDeductSegmentsAndInsertOrder() {
        stubJourney();
        when(seatInventoryMapper.selectSegmentsForUpdate(1L, TRAVEL_DATE, "二等座", 1, 3))
                .thenReturn(List.of(segment(5, "232.50"), segment(3, "344.50")));
        when(seatInventoryMapper.deductRange(1L, TRAVEL_DATE, "二等座", 1, 3)).thenReturn(2);

        OrderCreateVO vo = orderService.createOrder(request());

        assertEquals("G1", vo.getTrainNo());
        assertEquals("北京南", vo.getFromStation());
        assertEquals("上海虹桥", vo.getToStation());
        assertEquals(0, vo.getPrice().compareTo(new BigDecimal("577.00")));
        assertEquals("待支付", vo.getStatus());
        verify(ticketOrderMapper).insert(any(TicketOrder.class));
    }

    @Test
    void soldOutShouldFailBeforeDeduct() {
        stubJourney();
        when(seatInventoryMapper.selectSegmentsForUpdate(1L, TRAVEL_DATE, "二等座", 1, 3))
                .thenReturn(List.of(segment(0, "232.50"), segment(3, "344.50")));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> orderService.createOrder(request()));

        assertEquals(ErrorCode.SEAT_SOLD_OUT.getCode(), exception.getCode());
        verify(seatInventoryMapper, never()).deductRange(any(), any(), any(), any(), any());
        verifyNoInteractions(ticketOrderMapper);
    }

    @Test
    void deductConflictShouldFailWithoutInsert() {
        stubJourney();
        when(seatInventoryMapper.selectSegmentsForUpdate(1L, TRAVEL_DATE, "二等座", 1, 3))
                .thenReturn(List.of(segment(5, "232.50"), segment(3, "344.50")));
        when(seatInventoryMapper.deductRange(1L, TRAVEL_DATE, "二等座", 1, 3)).thenReturn(1);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> orderService.createOrder(request()));

        assertEquals(ErrorCode.SEAT_SOLD_OUT.getCode(), exception.getCode());
        verifyNoInteractions(ticketOrderMapper);
    }

    @Test
    void invalidStationOrderShouldFailWithTrainNotFound() {
        when(stationMapper.selectByName("北京南")).thenReturn(station(1L, "北京南"));
        when(stationMapper.selectByName("上海虹桥")).thenReturn(station(5L, "上海虹桥"));
        when(trainMapper.selectByTrainNo("G1")).thenReturn(train());
        when(trainMapper.selectStationOrder(1L, "北京南")).thenReturn(3);
        when(trainMapper.selectStationOrder(1L, "上海虹桥")).thenReturn(1);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> orderService.createOrder(request()));

        assertEquals(ErrorCode.TRAIN_NOT_FOUND.getCode(), exception.getCode());
        verifyNoInteractions(seatInventoryMapper, ticketOrderMapper);
    }

    @Test
    void failAfterDeductShouldThrowOrderCreateFailed() {
        stubJourney();
        when(seatInventoryMapper.selectSegmentsForUpdate(1L, TRAVEL_DATE, "二等座", 1, 3))
                .thenReturn(List.of(segment(5, "232.50"), segment(3, "344.50")));
        when(seatInventoryMapper.deductRange(1L, TRAVEL_DATE, "二等座", 1, 3)).thenReturn(2);
        OrderCreateRequest request = request();
        request.setFailAfterDeduct(true);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> orderService.createOrder(request));

        assertEquals(ErrorCode.ORDER_CREATE_FAILED.getCode(), exception.getCode());
        verify(ticketOrderMapper).insert(any(TicketOrder.class));
    }

    private void stubJourney() {
        when(stationMapper.selectByName("北京南")).thenReturn(station(1L, "北京南"));
        when(stationMapper.selectByName("上海虹桥")).thenReturn(station(5L, "上海虹桥"));
        when(trainMapper.selectByTrainNo("G1")).thenReturn(train());
        when(trainMapper.selectStationOrder(1L, "北京南")).thenReturn(1);
        when(trainMapper.selectStationOrder(1L, "上海虹桥")).thenReturn(3);
    }

    private OrderCreateRequest request() {
        OrderCreateRequest request = new OrderCreateRequest();
        request.setUserId(9001L);
        request.setTrainNo("G1");
        request.setTravelDate(TRAVEL_DATE);
        request.setFromStation("北京南");
        request.setToStation("上海虹桥");
        request.setSeatType("二等座");
        request.setPassengerName("张三");
        request.setPassengerIdCard("110101200001011234");
        return request;
    }

    private Train train() {
        Train train = new Train();
        train.setId(1L);
        train.setTrainNo("G1");
        return train;
    }

    private Station station(Long id, String name) {
        Station station = new Station();
        station.setId(id);
        station.setStationName(name);
        return station;
    }

    private SeatInventory segment(int remaining, String price) {
        SeatInventory segment = new SeatInventory();
        segment.setRemainingCount(remaining);
        segment.setPrice(new BigDecimal(price));
        return segment;
    }
}
