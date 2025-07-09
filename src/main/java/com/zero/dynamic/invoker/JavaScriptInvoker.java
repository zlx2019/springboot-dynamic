package com.zero.dynamic.invoker;

import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.graalvm.polyglot.*;
import org.graalvm.polyglot.io.IOAccess;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * JS 执行器
 *  - 预编译脚本为 {@link Source}
 *  - 所有{@link Context} 共享同一个 {@link Engine}, 借此缓存已编译的代码.
 *  - {@link Context} 是线程不安全的，不可多线程环境使用.
 * docs
 *  - <a href="https://www.graalvm.org/latest/reference-manual/js/FAQ/#performance">...</a>
 *
 *
 * JS 脚本执行器
 *
 *
 * @author Zero.
 * <p> Created on 2025/7/7 13:52 </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class JavaScriptInvoker {
    private final String LANGUAGE_ID = "js";
    private final Engine engine = Engine.create(LANGUAGE_ID);
    private final ConcurrentHashMap<String, Source> sources = new ConcurrentHashMap<>(16);
    private final ThreadLocal<Context> CTX = ThreadLocal.withInitial(this::getContext);

    /**
     * 注册脚本内容，预编译为Source进行缓存.
     * @param id        脚本ID
     * @param script    脚本内容
     */
    public void register(String id, String script){
        // 预编译脚本,并且缓存起来
        Source source = this.compileScript(script);
        log.info("engine sources size; {}", sources.size());
        CTX.get().eval(source);
        sources.put(id, source);
        log.info("register script success[{}]", id);
    }

    /**
     * 执行 JS 脚本
     *
     * @param id 脚本ID
     * @param funcName 函数名
     * @param args     函数参数
     *
     * @return {@link Value}
     */
    public String call(String id, String funcName, String args) {
//        Source source = sources.get(id);
//        if (Objects.isNull(source)) {
//            log.error("not found source [{}]", id);
//            return null;
//        }
//        try (var ctx = getContext()) {
//            ctx.eval(source);
//            Value func = ctx.getBindings(LANGUAGE_ID).getMember("decode");
//            Value ret = func.execute(content);
//            return ret.isNull() ? null : ret.asString();
//        }

        log.info("engine sources size; {}", sources.size());
        // TODO 为了保险起见，建议每次使用新的 Context 避免不必要的问题
        Value ret = Optional.ofNullable(sources.get(id)).map(source -> {
            // 编译脚本
            CTX.get().eval(source);
            // 获取脚本函数
            Value func = CTX.get().getBindings(LANGUAGE_ID).getMember(funcName);
            // 执行脚本函数
            return func.execute(args);
        }).orElse(null);
        return null == ret ? null : ret.toString();


        // 优化1: 预编译为 Source
//        Source source = sources.computeIfAbsent(script, this::compileScript);
//        // 执行源码
//        CTX.get().eval(source);
//        Value decodeFunc = CTX.get().getBindings(LANGUAGE_ID).getMember("decode");
//        Value ret = decodeFunc.execute();
//        return ret.isNull() ? null : ret.toString();


        // 获取执行上下文
//        try (Context ctx = getContext()) {
//            // 执行脚本，并返回结果.
//            Value ret = ctx.eval(LANGUAGE_ID, script);
//            // 返回脚本执行结果
//            return ret.isNull() ? null : ret.toString();
//        } catch (PolyglotException e) {
//            throw new RuntimeException(e);
//        }
    }

    public Source compileScript(String script) {
        try {
            return Source.newBuilder(LANGUAGE_ID, script, "").build();
        } catch (IOException e) {
            throw new RuntimeException("编译脚本异常", e);
        }
    }


    /**
     * 编译并运行脚本
     * @param script 脚本内容
     * @return {@link Value}
     */
    public Object eval(String script) {
        try (var stdout = new ByteArrayOutputStream(); var ctx = getContext(stdout)) {
            Source source = this.compileScript(script);
            Value ret = ctx.eval(source);
            String out = stdout.toString(StandardCharsets.UTF_8);
            return ret + "\n\n\n\n" + out;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @PreDestroy
    public void destroy(){
        CTX.get().close();
        CTX.remove();
    }


    /**
     * 创建执行上下文
     * @return {@link Context}
     */
    private Context getContext() {
        // 通过构建器创建执行上下文
        return Context.newBuilder(LANGUAGE_ID) // 可以执行的语言，可设置多种.
                .allowAllAccess(false) // 设置上下文所有权限的默认值
                .allowCreateProcess(false)  // 是否允许创建进程
                .allowCreateThread(false)   // 是否允许创建线程
                .allowEnvironmentAccess(EnvironmentAccess.NONE) // 是否可以访问环境
                .allowExperimentalOptions(false) // 是否允许实验性选项
                .allowHostAccess(HostAccess.NONE)        // 访问Java对象和方法策略
                .allowHostClassLookup(s -> true)    //  指定允许从 JavaScript 中查找哪些 Java 类。
                .allowIO(IOAccess.NONE)             // 是否允许IO操作
                .allowNativeAccess(false) // 是否允许原生访问
                .timeZone(ZoneId.systemDefault()) // 设置时区
                // 额外配置
                .option("js.ecmascript-version", "2024")  // ES 版本
                .out(System.out)
                .err(System.err)
                .in(System.in)
                .engine(engine)
                .build();
    }

    public Context getContext(ByteArrayOutputStream out) {
        return Context.newBuilder(LANGUAGE_ID)
                .allowAllAccess(false)
                .allowCreateProcess(false)
                .allowCreateThread(false)
                .allowEnvironmentAccess(EnvironmentAccess.NONE)
                .allowExperimentalOptions(false)
                .allowHostAccess(HostAccess.NONE)
                .allowHostClassLookup(s -> true)
                .allowIO(IOAccess.NONE)
                .allowNativeAccess(false)
                .timeZone(ZoneId.systemDefault())
                // 额外配置
                .option("js.ecmascript-version", "2024")
                .out(out)
                .err(System.err)
                .in(System.in)
                .engine(engine)
                .build();
    }

}
