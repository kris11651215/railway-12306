package com.railway.service;

import com.railway.algorithm.AStarRoutePlanner;
import com.railway.algorithm.DijkstraRoutePlanner;
import com.railway.algorithm.RouteOptions;
import com.railway.algorithm.RoutePlanner;
import com.railway.algorithm.RouteStrategy;
import com.railway.algorithm.TimeExpandedGraph;
import com.railway.algorithm.TransferGraphProvider;
import com.railway.common.ErrorCode;
import com.railway.config.RouteProperties;
import com.railway.dto.TransferPlanVO;
import com.railway.exception.BusinessException;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

@Service
public class TransferRouteService {

    private final TransferGraphProvider graphProvider;
    private final DijkstraRoutePlanner dijkstraPlanner;
    private final AStarRoutePlanner aStarPlanner;
    private final RouteProperties properties;

    public TransferRouteService(TransferGraphProvider graphProvider, DijkstraRoutePlanner dijkstraPlanner,
                                AStarRoutePlanner aStarPlanner, RouteProperties properties) {
        this.graphProvider = graphProvider;
        this.dijkstraPlanner = dijkstraPlanner;
        this.aStarPlanner = aStarPlanner;
        this.properties = properties;
    }

    public TransferPlanVO plan(String from, String to, String strategyCode, LocalDate travelDate) {
        if (from == null || from.isBlank() || to == null || to.isBlank()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "from、to 为必填参数");
        }
        if (from.equals(to)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "出发站与到达站不能相同");
        }
        RouteStrategy strategy;
        try {
            strategy = RouteStrategy.fromCode(strategyCode);
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, e.getMessage());
        }
        LocalDate date = travelDate == null ? LocalDate.now() : travelDate;
        TimeExpandedGraph graph = graphProvider.graph(date);
        if (!graph.containsStation(from)) {
            throw new BusinessException(ErrorCode.STATION_NOT_FOUND, "出发站不存在或暂无可达车次：" + from);
        }
        if (!graph.containsStation(to)) {
            throw new BusinessException(ErrorCode.STATION_NOT_FOUND, "到达站不存在或暂无可达车次：" + to);
        }
        RoutePlanner planner = "astar".equalsIgnoreCase(properties.getAlgorithm()) ? aStarPlanner : dijkstraPlanner;
        RouteOptions options = new RouteOptions(properties.getMaxTransfers(), properties.getMaxDurationMinutes());
        TransferPlanVO plan = planner.plan(graph, from, to, strategy, options);
        if (plan == null) {
            throw new BusinessException(ErrorCode.ROUTE_NOT_FOUND, "未找到 " + from + " 到 " + to + " 的可行换乘方案");
        }
        plan.setAlgorithm(planner.algorithmName());
        return plan;
    }
}
