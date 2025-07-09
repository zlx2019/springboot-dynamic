package com.zero.dynamic.pool;

import com.zero.dynamic.pool.impl.InvokerJSWork;
import org.graalvm.polyglot.Value;

import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * --enable-native-access=ALL-UNNAMED --add-opens=java.base/java.lang=ALL-UNNAMED --add-opens=java.base/jdk.internal.misc=ALL-UNNAMED --add-exports=java.base/jdk.internal.misc=ALL-UNNAMED
 *
 * @author Zero.
 * <p> Created on 2025/7/8 16:50 </p>
 */
public class WorkerPoolInvoke implements Worker, AutoCloseable{
    /// 所属脚本对象池
    private final WorkerPool pool;
    /// 脚本实例
    private final AbstractWorker worker;
    /// 实例是否已用完并归还到池中
    private final AtomicBoolean closed = new AtomicBoolean(false);

    public WorkerPoolInvoke(WorkerPool pool) throws InterruptedException {
        this.pool = pool;
        this.worker = pool.take();
    }

    public WorkerPoolInvoke(WorkerPool pool, Duration timeout) throws InterruptedException {
        this.pool = pool;
        this.worker = pool.getWorkers().poll(timeout.toMillis(), TimeUnit.MILLISECONDS);
    }

    /**
     * 执行脚本函数
     * @param args  函数的入参
     * @return 函数出参
     */
    @Override
    public Value call(Object args) {
        if (closed.get())
            throw new RuntimeException("worker already closed");
        return worker.call(args);
    }

    /**
     * 将实例放入池中
     */
    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            this.pool.offer(worker);
        }
    }

    public static void main(String[] args) throws Exception {
        var script = """
(function () {
    const patterns = [
      /(\\d{5,6})/,
      /<b>(\\d{6})<\\/b>/,
      /verification page:[\\s\\S]*?(\\d{6})[\\s\\S]*?/,
      /<b><p>(\\d{6})<\\/p></,
      />\\s*<b>(\\d{6})<\\/b>/,
      /\\n(\\d{6})\\r/
    ];
    return function (content) {
    for (const pat of patterns) {
        const match = content.match(pat);
        if (match) {
            return match.slice(1);
        }
    }
    return null;
    }
})()
""";
        // 为脚本创建实例池, 共创建5个实例对象
        try (WorkerPool pool = new WorkerPool(1, () -> new InvokerJSWork(script))){
            // 从池中获取一个实例
            try (Worker worker = pool.poll()){
                // 调用函数
                System.out.println(worker.call("dnwajkdnjwak 21341 dnjkawdwa avdaw").getArrayElement(0).asString());
                System.out.println(worker.call("dnwajkdnjwak 21341 dnjkawdwa avdaw").getArrayElement(0).asString());
                System.out.println(worker.call("dnwajkdnjwak 21341 dnjkawdwa avdaw").getArrayElement(0).asString());
            }
        }



    }
}
