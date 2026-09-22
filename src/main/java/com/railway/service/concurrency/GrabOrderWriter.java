package com.railway.service.concurrency;

import com.railway.dto.OrderCreateRequest;
import com.railway.dto.OrderCreateVO;

public interface GrabOrderWriter {

    OrderCreateVO create(OrderCreateRequest request);

    long count();

    boolean supportsReset();

    void reset();
}
