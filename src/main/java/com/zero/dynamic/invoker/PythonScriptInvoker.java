package com.zero.dynamic.invoker;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Python 脚本执行器
 *
 * @author Zero.
 * <p> Created on 2025/7/7 13:53 </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PythonScriptInvoker {
    private final String LANGUAGE = "python";
    private final Engine engine = Engine.create(LANGUAGE);
    private final Map<String, Source> sources = new HashMap<>(32);
    private final ThreadLocal<Context> CTX = ThreadLocal.withInitial(()-> this.getContext(null));

    /**
     * 注册脚本，预编译后缓存.
     *
     * @param id        脚本ID
     * @param script    脚本内容
     */
    public void register(String id, String script){
        Source source = Source.create(LANGUAGE, script);
        CTX.get().eval(source);
        sources.put(id, source);
    }

    /**
     * 执行 Python 脚本中的函数
     * @param id        脚本ID
     * @param funcName  函数名
     * @param args      函数参数
     */
    public Object call(String id, String funcName, String args){
        return Optional.ofNullable(sources.get(id)).map(source -> {
            CTX.get().eval(source);
            Value func = CTX.get().getBindings(LANGUAGE).getMember(funcName);
            Value ret = func.execute(args);
            return ret.isNull() ? null : ret.asString();
        }).orElse(null);
    }





    /**
     * 执行 Python 脚本
     * @param script 脚本内容
     * @return {@link Value}
     */
    public Value eval(String script) {
        return CTX.get().eval(LANGUAGE, script);
    }

    /**
     * 创建执行上下文
     * @return {@link Context}
     */
    private Context getContext(ByteArrayOutputStream stdout) {
        Context ctx = Context.newBuilder(LANGUAGE)
                .out(stdout == null ? System.out : stdout)
                .in(System.in)
                .err(System.err)
                .engine(engine)
                .build();
        ctx.initialize(LANGUAGE);
        return ctx;
    }
}
