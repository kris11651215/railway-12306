package com.railway.algorithm;

import org.springframework.stereotype.Component;

@Component
public class DijkstraRoutePlanner extends AbstractRoutePlanner {

    @Override
    public String algorithmName() {
        return "dijkstra";
    }
}
