package com.railway.mapper;

import com.railway.entity.SeatInventory;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;

@Mapper
public interface SeatInventoryMapper {

    List<SeatInventory> selectSegmentsForUpdate(@Param("trainId") Long trainId,
                                                @Param("travelDate") LocalDate travelDate,
                                                @Param("seatType") String seatType,
                                                @Param("fromOrder") Integer fromOrder,
                                                @Param("toOrder") Integer toOrder);

    int deductRange(@Param("trainId") Long trainId,
                    @Param("travelDate") LocalDate travelDate,
                    @Param("seatType") String seatType,
                    @Param("fromOrder") Integer fromOrder,
                    @Param("toOrder") Integer toOrder);
}
