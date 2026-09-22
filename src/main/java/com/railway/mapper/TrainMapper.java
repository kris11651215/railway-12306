package com.railway.mapper;

import com.railway.dto.SegmentPriceRow;
import com.railway.dto.TrainStopRow;
import com.railway.dto.TrainVO;
import com.railway.entity.Train;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;

@Mapper
public interface TrainMapper {

    Train selectByTrainNo(@Param("trainNo") String trainNo);

    Integer selectStationOrder(@Param("trainId") Long trainId, @Param("stationName") String stationName);

    List<TrainStopRow> selectAllStops();

    List<SegmentPriceRow> selectSegmentPrices(@Param("travelDate") LocalDate travelDate,
                                              @Param("seatType") String seatType);

    List<TrainVO> queryTrains(@Param("from") String from,
                              @Param("to") String to,
                              @Param("travelDate") LocalDate travelDate,
                              @Param("seatType") String seatType,
                              @Param("trainType") String trainType);
}
