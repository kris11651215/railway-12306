package com.railway.mapper;

import com.railway.entity.Station;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface StationMapper {

    Station selectByName(String stationName);
}
