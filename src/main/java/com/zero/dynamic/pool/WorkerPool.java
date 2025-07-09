package com.zero.dynamic.pool;

import lombok.Data;

import java.time.Duration;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.function.Supplier;

/**
 * 对象池，对应一个动态脚本，缓存多份上下文执行环境.
 *
 * @author Zero.
 * <p> Created on 2025/7/8 16:26 </p>
 */
@Data
public class WorkerPool implements AutoCloseable {
    /** 对象池大小 */
    private final int size;
    /** 脚本源 */
    // private final Source source;
    /** 是否已释放 */
    private volatile boolean closed = false;
    /** 缓冲队列 */
    private final ArrayBlockingQueue<AbstractWorker> workers;

    public WorkerPool(int size, Supplier<AbstractWorker> supplier) {
        this.size = size;
        this.workers = new ArrayBlockingQueue<>(size);
        for (int i = 0; i < size; i++) {
            this.workers.offer(supplier.get());
        }
    }

    /**
     * 获取脚本实例（阻塞式，直到获取到）
     *
     * @return {@link Worker}
     */
    public Worker poll() throws InterruptedException {
        if (closed)
            throw new RuntimeException("pool already closed");
        return new WorkerPoolInvoke(this);
    }

    /**
     * 获取脚本实例（阻塞式，直到获取到或者超时）
     *
     * @param timeout 超时时间
     */
    public Worker poll(Duration timeout) throws InterruptedException {
        if (closed)
            throw new RuntimeException("pool already closed");
        return new WorkerPoolInvoke(this, timeout);
    }



    /**
     * 从池中获取脚本实例
     *
     * @return {@link AbstractWorker}
     */
    AbstractWorker take() throws InterruptedException {
        if (closed)
            throw new RuntimeException("pool already closed");
        return workers.take();
    }

    /**
     * 将实例放入池中
     *
     * @param worker 脚本实例
     */
    void offer(AbstractWorker worker) {
        workers.offer(worker);
    }


    /**
     * 释放对象池资源
     */
    @Override
    public void close() {
        if (closed)
            return;
        closed = true;
        for (int i = 0; i < size; i++) {
            try {
                workers.take().close();
            } catch (Exception e) {
                throw new RuntimeException("worker closed", e);
            }
        }
    }
}
