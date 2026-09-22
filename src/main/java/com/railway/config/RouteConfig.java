package com.railway.config;

import com.railway.algorithm.DbTransferGraphProvider;
import com.railway.algorithm.MemoryTransferGraphProvider;
import com.railway.algorithm.TransferGraphProvider;
import com.railway.mapper.TrainMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RouteConfig {

    @Bean
    @ConditionalOnProperty(name = "railway.route.mode", havingValue = "memory", matchIfMissing = true)
    public TransferGraphProvider memoryTransferGraphProvider(RouteProperties properties) {
        return new MemoryTransferGraphProvider(properties);
    }

    @Bean
    @ConditionalOnProperty(name = "railway.route.mode", havingValue = "db")
    public TransferGraphProvider dbTransferGraphProvider(TrainMapper trainMapper, RouteProperties properties) {
        return new DbTransferGraphProvider(trainMapper, properties);
    }
}
