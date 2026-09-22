package com.railway.mapper;

import com.railway.entity.TicketOrder;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface TicketOrderMapper {

    int insert(TicketOrder order);

    TicketOrder selectByOrderNo(String orderNo);
}
