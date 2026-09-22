package com.railway.service.concurrency;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public class GrabQueue {

    private static final Logger log = LoggerFactory.getLogger(GrabQueue.class);
    private static final long POLL_INTERVAL_MILLIS = 200L;

    private final BlockingQueue<GrabMessage> queue;
    private final GrabProcessor processor;
    private final Consumer<GrabResult> completionHandler;
    private final List<Thread> workers = new ArrayList<>();
    private volatile boolean running = true;

    public GrabQueue(GrabProcessor processor, int capacity, int workerCount,
                     Consumer<GrabResult> completionHandler) {
        this.processor = processor;
        this.completionHandler = completionHandler;
        this.queue = new ArrayBlockingQueue<>(Math.max(capacity, 1));
        for (int i = 0; i < Math.max(workerCount, 0); i++) {
            Thread worker = new Thread(this::consumeLoop, "grab-worker-" + i);
            worker.setDaemon(true);
            workers.add(worker);
            worker.start();
        }
    }

    public boolean submit(GrabMessage message) {
        return queue.offer(message);
    }

    public int size() {
        return queue.size();
    }

    public int remainingCapacity() {
        return queue.remainingCapacity();
    }

    public int getCapacity() {
        return queue.size() + queue.remainingCapacity();
    }

    public int getWorkerCount() {
        return workers.size();
    }

    public boolean isRunning() {
        return running;
    }

    private void consumeLoop() {
        while (running) {
            try {
                GrabMessage message = queue.poll(POLL_INTERVAL_MILLIS, TimeUnit.MILLISECONDS);
                if (message == null) {
                    continue;
                }
                GrabResult result = processor.process(message);
                completionHandler.accept(result);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            } catch (Exception e) {
                log.error("抢票消费者异常", e);
            }
        }
    }

    public void shutdown() {
        running = false;
        for (Thread worker : workers) {
            worker.interrupt();
        }
        for (Thread worker : workers) {
            try {
                worker.join(500L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }
}
