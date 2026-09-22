package com.railway.service;

import com.railway.common.ErrorCode;
import com.railway.dto.OrderCreateRequest;
import com.railway.dto.OrderCreateVO;
import com.railway.entity.SeatInventory;
import com.railway.entity.Station;
import com.railway.entity.TicketOrder;
import com.railway.entity.TicketStatus;
import com.railway.entity.Train;
import com.railway.exception.BusinessException;
import com.railway.mapper.SeatInventoryMapper;
import com.railway.mapper.StationMapper;
import com.railway.mapper.TicketOrderMapper;
import com.railway.mapper.TrainMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);
    private static final String DEFAULT_SEAT_TYPE = "二等座";
    private static final String DEFAULT_ID_CARD = "110101********0000";
    private static final DateTimeFormatter ORDER_NO_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final StationMapper stationMapper;
    private final TrainMapper trainMapper;
    private final SeatInventoryMapper seatInventoryMapper;
    private final TicketOrderMapper ticketOrderMapper;

    public OrderService(StationMapper stationMapper, TrainMapper trainMapper,
                        SeatInventoryMapper seatInventoryMapper, TicketOrderMapper ticketOrderMapper) {
        this.stationMapper = stationMapper;
        this.trainMapper = trainMapper;
        this.seatInventoryMapper = seatInventoryMapper;
        this.ticketOrderMapper = ticketOrderMapper;
    }

    @Transactional(rollbackFor = Exception.class)
    public OrderCreateVO createOrder(OrderCreateRequest request) {
        OrderRequestValidator.validate(request);

        Station from = stationMapper.selectByName(request.getFromStation());
        if (from == null) {
            throw new BusinessException(ErrorCode.STATION_NOT_FOUND, "出发站不存在：" + request.getFromStation());
        }
        Station to = stationMapper.selectByName(request.getToStation());
        if (to == null) {
            throw new BusinessException(ErrorCode.STATION_NOT_FOUND, "到达站不存在：" + request.getToStation());
        }
        Train train = trainMapper.selectByTrainNo(request.getTrainNo());
        if (train == null) {
            throw new BusinessException(ErrorCode.TRAIN_NOT_FOUND, "车次不存在：" + request.getTrainNo());
        }

        Integer fromOrder = trainMapper.selectStationOrder(train.getId(), from.getStationName());
        Integer toOrder = trainMapper.selectStationOrder(train.getId(), to.getStationName());
        if (fromOrder == null || toOrder == null || fromOrder >= toOrder) {
            throw new BusinessException(ErrorCode.TRAIN_NOT_FOUND,
                    "车次 " + train.getTrainNo() + " 不经过 " + from.getStationName() + " → " + to.getStationName() + " 区间");
        }

        String seatType = (request.getSeatType() == null || request.getSeatType().isBlank())
                ? DEFAULT_SEAT_TYPE : request.getSeatType();

        List<SeatInventory> segments = seatInventoryMapper.selectSegmentsForUpdate(
                train.getId(), request.getTravelDate(), seatType, fromOrder, toOrder);
        if (segments.isEmpty()) {
            throw new BusinessException(ErrorCode.TRAIN_NOT_FOUND, "该日期没有可售库存记录");
        }
        for (SeatInventory segment : segments) {
            if (segment.getRemainingCount() < 1) {
                throw new BusinessException(ErrorCode.SEAT_SOLD_OUT, seatType + " 余票不足");
            }
        }

        int expectedSegments = toOrder - fromOrder;
        int updated = seatInventoryMapper.deductRange(
                train.getId(), request.getTravelDate(), seatType, fromOrder, toOrder);
        if (updated != expectedSegments) {
            throw new BusinessException(ErrorCode.SEAT_SOLD_OUT, "库存扣减冲突，请重试");
        }

        BigDecimal price = segments.stream()
                .map(SeatInventory::getPrice)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        TicketOrder order = new TicketOrder();
        order.setOrderNo(generateOrderNo());
        order.setUserId(request.getUserId());
        order.setTrainId(train.getId());
        order.setTravelDate(request.getTravelDate());
        order.setFromStationId(from.getId());
        order.setToStationId(to.getId());
        order.setFromStationOrder(fromOrder);
        order.setToStationOrder(toOrder);
        order.setSeatType(seatType);
        order.setPassengerName(request.getPassengerName());
        order.setPassengerIdCard(maskIdCard(request.getPassengerIdCard()));
        order.setPrice(price);
        order.setStatus(TicketStatus.PENDING_PAYMENT);
        order.setExpireAt(LocalDateTime.now().plusMinutes(15));
        ticketOrderMapper.insert(order);

        if (Boolean.TRUE.equals(request.getFailAfterDeduct())) {
            throw new BusinessException(ErrorCode.ORDER_CREATE_FAILED,
                    "模拟异常：扣库存与订单必须一起回滚");
        }

        log.info("下单成功: orderNo={}, train={}, {}→{}, price={}",
                order.getOrderNo(), train.getTrainNo(), from.getStationName(), to.getStationName(), price);

        OrderCreateVO vo = new OrderCreateVO();
        vo.setOrderId(order.getId());
        vo.setOrderNo(order.getOrderNo());
        vo.setTrainNo(train.getTrainNo());
        vo.setFromStation(from.getStationName());
        vo.setToStation(to.getStationName());
        vo.setSeatType(seatType);
        vo.setPrice(price);
        vo.setStatus(order.getStatus().getChineseName());
        vo.setExpireAt(order.getExpireAt());
        return vo;
    }

    private String generateOrderNo() {
        return "ORD" + LocalDateTime.now().format(ORDER_NO_FORMAT)
                + ThreadLocalRandom.current().nextInt(1000, 9999);
    }

    private String maskIdCard(String idCard) {
        if (idCard == null || idCard.isBlank()) {
            return DEFAULT_ID_CARD;
        }
        if (idCard.length() <= 10) {
            return idCard;
        }
        return idCard.substring(0, 6) + "********" + idCard.substring(idCard.length() - 4);
    }
}
