package com.zero.dynamic.pool;

import org.graalvm.polyglot.Value;

/**
 * 动态函数调用.
 *
 * @author Zero.
 * <p> Created on 2025/7/8 10:05 </p>
 */
public interface Worker extends AutoCloseable {
    /**
     * 调用脚本函数
     *
     * @param args              函数的入参
     * @return {@link Value}    函数的出参
     */
    Value call(Object args);
}
