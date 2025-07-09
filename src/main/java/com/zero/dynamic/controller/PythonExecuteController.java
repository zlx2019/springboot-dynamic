package com.zero.dynamic.controller;

import com.zero.dynamic.invoker.PythonScriptInvoker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.graalvm.polyglot.Value;
import org.springframework.web.bind.annotation.*;

/**
 * Python 脚本管理
 *
 * @author Zero.
 * <p> Created on 2025/7/7 13:49 </p>
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/python")
public class PythonExecuteController {
    private final PythonScriptInvoker pythonScriptInvoker;

    /**
     * 注册 Python 脚本
     * @param id    脚本ID
     * @param script 脚本内容
     */
    @PostMapping("/register")
    public String register(@RequestParam String id, @RequestBody String script) {
        pythonScriptInvoker.register(id, script);
        return id;
    }

    /**
     * 调用 Python 脚本中的函数
     * @param id      脚本ID
     * @param content 函数参数
     */
    @GetMapping("/execute")
    public Object javascript(@RequestParam String id, @RequestParam String content) {
        return pythonScriptInvoker.call(id, "decode", content);
    }

    /**
     * 编译并且执行 JavaScript 脚本.
     * @param script 脚本内容
     */
    @PostMapping("/eval")
    public Object eval(@RequestBody String script) {
        Value ret = pythonScriptInvoker.eval(script);
        return ret.isNull() ? null : ret.asString();
    }

}
