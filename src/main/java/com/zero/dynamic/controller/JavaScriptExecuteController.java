package com.zero.dynamic.controller;

import com.zero.dynamic.invoker.JavaScriptInvoker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 * 执行各种脚本语言
 *
 * @author Zero.
 * <p> Created on 2025/7/7 13:49 </p>
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/script/js")
public class JavaScriptExecuteController {
    private final JavaScriptInvoker jsInvoker;

    /**
     * 注册脚本
     * @param id    脚本ID
     * @param script 脚本内容
     */
    @PostMapping("/register")
    public String register(@RequestParam String id, @RequestBody String script) {
        jsInvoker.register(id, script);
        return id;
    }

    /**
     * 执行JS脚本中的函数
     * @param id      脚本ID
     * @param content 参数
     */
    @GetMapping("/execute")
    public Object javascript(@RequestParam String id, @RequestParam String content) {
        String ret = jsInvoker.call(id, "decode", content);
        log.info("ret: {}", ret);
        return ret;
    }

    /**
     * 编译并且执行 JavaScript 脚本.
     * @param script 脚本内容
     */
    @PostMapping("/eval")
    public Object eval(@RequestBody String script) {
        return jsInvoker.eval(script);
    }

}
