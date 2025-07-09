package com.zero.dynamic.pool;

import lombok.extern.slf4j.Slf4j;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;

import java.util.Objects;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 动态脚本实例, 将上下文{@link Context} 和动态函数{@link Value} 缓存起来, 借此提高执行效率。
 *
 * @author Zero.
 * <p> Created on 2025/7/8 10:09 </p>
 */
@Slf4j
public class AbstractWorker implements Worker, AutoCloseable {
    /**
     * 外部语言的执行上下文
     */
    private Context context = null;
    /**
     * 要动态执行的函数
     */
    private final Value func;
    /**
     * {@link Context} 和 {@link Value} 都是线程不安全的，所以需要加锁.
     */
    private final ReentrantLock lock = new ReentrantLock();


    protected AbstractWorker(String language, String script, String funcName) {
        try {
            // 构建脚本
            Source source = Source.create(language, script);
            // 构建执行上下文 TODO 更多详细配置
            this.context = Context.create(language);
            // 编译脚本
            Value value = this.context.eval(source);
            // 获取脚本的函数句柄
            if (funcName == null) {
                if (!value.canExecute()) {
                    throw new RuntimeException("script IIFE function is not executable");
                }
                this.func = value;
            }else {
                Value member = this.context.getBindings(language).getMember(funcName);
                if (member == null || !member.canExecute()){
                    throw new RuntimeException("script non executable functions: " + funcName);
                }
                this.func = member;
            }
        }catch (Exception e){
            if (Objects.nonNull(this.context)){
                context.close();
            }
            throw new RuntimeException("failed to init worker", e);
        }

    }

    /**
     * 执行函数
     */
    @Override
    public Value call(Object args) {
        try {
            lock.lock();
            return this.func.execute(args);
        }finally {
            lock.unlock();
        }
    }

    /**
     * 释放脚本上下文资源.
     */
    @Override
    public void close() throws Exception {
        try {
            lock.lock();
            context.close();
        }finally {
            lock.unlock();
        }
    }
}
