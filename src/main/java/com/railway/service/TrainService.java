package com.railway.service;

import com.railway.common.ErrorCode;
import com.railway.dto.TrainVO;
import com.railway.entity.Direction;
import com.railway.entity.TrainType;
import com.railway.exception.BusinessException;
import com.railway.mapper.StationMapper;
import com.railway.mapper.TrainMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

@Service
public class TrainService {

    private static final String DEFAULT_SEAT_TYPE = "二等座";

    private final StationMapper stationMapper;
    private final TrainMapper trainMapper;

    public TrainService(StationMapper stationMapper, TrainMapper trainMapper) {
        this.stationMapper = stationMapper;
        this.trainMapper = trainMapper;
    }

    public List<TrainVO> queryTrains(String from, String to, LocalDate travelDate,
                                     String seatType, String trainType) {
        if (from == null || from.isBlank() || to == null || to.isBlank() || travelDate == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "from、to、date 为必填参数");
        }
        if (from.equals(to)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "出发站与到达站不能相同");
        }
        if (stationMapper.selectByName(from) == null) {
            throw new BusinessException(ErrorCode.STATION_NOT_FOUND, "出发站不存在：" + from);
        }
        if (stationMapper.selectByName(to) == null) {
            throw new BusinessException(ErrorCode.STATION_NOT_FOUND, "到达站不存在：" + to);
        }

        String actualSeatType = (seatType == null || seatType.isBlank()) ? DEFAULT_SEAT_TYPE : seatType;
        List<TrainVO> trains = trainMapper.queryTrains(from, to, travelDate, actualSeatType, trainType);
        for (TrainVO vo : trains) {
            vo.setSeatType(actualSeatType);
            vo.setTrainTypeName(TrainType.valueOf(vo.getTrainType()).getChineseName());
            vo.setDirectionName(Direction.valueOf(vo.getDirection()).getChineseName());
        }
        return trains;
    }
}
