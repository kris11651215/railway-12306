package com.railway.algorithm;

import com.railway.dto.TransferPlanVO;

public interface RoutePlanner {

    String algorithmName();

    TransferPlanVO plan(TimeExpandedGraph graph, String fromStation, String toStation,
                        RouteStrategy strategy, RouteOptions options);
}
