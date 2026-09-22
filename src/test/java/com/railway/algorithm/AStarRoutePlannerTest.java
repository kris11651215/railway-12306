package com.railway.algorithm;

import com.railway.dto.TransferPlanVO;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AStarRoutePlannerTest {

    private final DijkstraRoutePlanner dijkstra = new DijkstraRoutePlanner();
    private final AStarRoutePlanner aStar = new AStarRoutePlanner();
    private final RouteOptions options = new RouteOptions(2, 1440);

    @Test
    void fastestShouldMatchDijkstra() {
        TransferPlanVO dijkstraPlan = dijkstra.plan(DijkstraRoutePlannerTest.demoGraph(),
                "甲城", "乙城", RouteStrategy.FASTEST, options);
        TransferPlanVO aStarPlan = aStar.plan(DijkstraRoutePlannerTest.demoGraph(),
                "甲城", "乙城", RouteStrategy.FASTEST, options);
        assertEquals(dijkstraPlan.getTotalDurationMinutes(), aStarPlan.getTotalDurationMinutes());
        assertEquals(dijkstraPlan.getTotalPrice(), aStarPlan.getTotalPrice());
        assertEquals(dijkstraPlan.getLegs().size(), aStarPlan.getLegs().size());
        assertEquals("astar", aStar.algorithmName());
    }

    @Test
    void cheapestShouldMatchDijkstra() {
        TransferPlanVO dijkstraPlan = dijkstra.plan(DijkstraRoutePlannerTest.demoGraph(),
                "甲城", "乙城", RouteStrategy.CHEAPEST, options);
        TransferPlanVO aStarPlan = aStar.plan(DijkstraRoutePlannerTest.demoGraph(),
                "甲城", "乙城", RouteStrategy.CHEAPEST, options);
        assertEquals(dijkstraPlan.getTotalDurationMinutes(), aStarPlan.getTotalDurationMinutes());
        assertEquals(dijkstraPlan.getTotalPrice(), aStarPlan.getTotalPrice());
        assertEquals(dijkstraPlan.getTransferCount(), aStarPlan.getTransferCount());
    }
}
