package com.railway.config;

import com.railway.service.OrderService;
import com.railway.service.concurrency.DbGrabOrderWriter;
import com.railway.service.concurrency.DistributedLockManager;
import com.railway.service.concurrency.GrabOrderWriter;
import com.railway.service.concurrency.GrabProcessor;
import com.railway.service.concurrency.GrabProperties;
import com.railway.service.concurrency.GrabQueue;
import com.railway.service.concurrency.GrabResultRepository;
import com.railway.service.concurrency.GrabService;
import com.railway.service.concurrency.InMemoryDistributedLockManager;
import com.railway.service.concurrency.InMemoryGrabOrderWriter;
import com.railway.service.concurrency.InMemoryStockStore;
import com.railway.service.concurrency.RedisDistributedLockManager;
import com.railway.service.concurrency.RedisStockStore;
import com.railway.service.concurrency.StockStore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

@Configuration
public class GrabConfig {

    @Bean
    @ConditionalOnProperty(name = "railway.redis.mode", havingValue = "redis")
    public DistributedLockManager redisDistributedLockManager(StringRedisTemplate redisTemplate) {
        return new RedisDistributedLockManager(redisTemplate);
    }

    @Bean
    @ConditionalOnProperty(name = "railway.redis.mode", havingValue = "memory", matchIfMissing = true)
    public DistributedLockManager inMemoryDistributedLockManager() {
        return new InMemoryDistributedLockManager();
    }

    @Bean
    @ConditionalOnProperty(name = "railway.redis.mode", havingValue = "redis")
    public StockStore redisStockStore(StringRedisTemplate redisTemplate) {
        return new RedisStockStore(redisTemplate);
    }

    @Bean
    @ConditionalOnProperty(name = "railway.redis.mode", havingValue = "memory", matchIfMissing = true)
    public StockStore inMemoryStockStore() {
        return new InMemoryStockStore();
    }

    @Bean
    @ConditionalOnProperty(name = "railway.grab.order-store", havingValue = "db")
    public GrabOrderWriter dbGrabOrderWriter(OrderService orderService) {
        return new DbGrabOrderWriter(orderService);
    }

    @Bean
    @ConditionalOnProperty(name = "railway.grab.order-store", havingValue = "memory", matchIfMissing = true)
    public GrabOrderWriter inMemoryGrabOrderWriter(GrabProperties properties) {
        return new InMemoryGrabOrderWriter(properties.getSimulateOrderCostMillis());
    }

    @Bean
    public GrabResultRepository grabResultRepository() {
        return new GrabResultRepository();
    }

    @Bean
    public GrabProcessor grabProcessor(DistributedLockManager lockManager, StockStore stockStore,
                                       GrabOrderWriter orderWriter, GrabProperties properties) {
        return new GrabProcessor(lockManager, stockStore, orderWriter,
                properties.getLockWaitMillis(), properties.getLockLeaseMillis());
    }

    @Bean
    public GrabQueue grabQueue(GrabProcessor grabProcessor, GrabResultRepository resultRepository,
                               GrabProperties properties) {
        return new GrabQueue(grabProcessor, properties.getQueueCapacity(),
                properties.getWorkerCount(), resultRepository::markFinished);
    }

    @Bean
    public GrabService grabService(GrabQueue grabQueue, GrabProcessor grabProcessor,
                                   StockStore stockStore, GrabOrderWriter orderWriter,
                                   GrabResultRepository resultRepository, GrabProperties properties) {
        return new GrabService(grabQueue, grabProcessor, stockStore, orderWriter,
                resultRepository, properties);
    }
}
