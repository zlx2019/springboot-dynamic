package com.zero.dynamic.pool.impl;

import com.zero.dynamic.pool.AbstractWorker;
import org.graalvm.polyglot.Value;

import java.util.random.RandomGenerator;

/**
 * @author Zero.
 * <p> Created on 2025/7/8 10:31 </p>
 */
public class InvokerPythonWorker extends AbstractWorker {
    InvokerPythonWorker(String script) {
        super("python", script, "decode");
    }

    public static void main(String[] args) throws Exception {
        String script = """
                import re
                patterns = [
                        r"<b>(\\d{6})<\\/b>",
                        r"verification page:[\\s\\S]*?(\\d{6})[\\s\\S]*?",
                        r"<b><p>(\\d{6})<\\/p>",
                        r">\\s*<b>(\\d{6})<\\/b>",
                        r"\\n(\\d{6})\\r"
                    ]
                
                def decode(content):
                    for pat in patterns:
                        match = re.search(pat, content)
                        if match:
                            # 返回整个匹配到的字符串
                            return match.groups()
                    return None
                """;
        String content = """
                你已选择此电子邮件地址作为你的 Apple 账户。为验证此电子邮件地址属于你，请在电子邮件验证页面输入下方验证码：\\r\\n\\r\\n <b>%d</b> \\r\\n\\r\\n此电子邮件发出 3 小时后，验证码将过期。\\r\\n\\r\\n你收到此电子邮件的原因：\\r\\nApple 会在你选择电子邮件地址为 Apple 账户时提出验证要求。你的 Apple 账户需经过验证才能使用。\\r\\n\\r\\n如果你未提出此请求，可以忽略这封电子邮件。未经过验证便无法创建 Apple 账户。\\r\\n\\r\\n\\r\\n\\r\\n\\r\\n-------------------------------------------------------------\\r\\n\\r\\nApple 账户\\r\\nhttps://account.apple.com\\r\\n\\r\\n支持\\r\\nhttps://www.apple.com/support/\\r\\n\\r\\n隐私政策\\r\\nhttps://www.apple.com/legal/privacy\\r\\n\\r\\n\\r\\nCopyright (c) 2025 One Apple Park Way, Cupertino, CA 95014, United States 保留所有权利。
                """;
        RandomGenerator generator = RandomGenerator.getDefault();
        try (var worker = new InvokerPythonWorker(script)){
            for (int i = 0; i < 10; i++) {
                int mockCode = generator.nextInt(100000, 999999);
                String mockContent = String.format(content, mockCode);
                var start = - System.nanoTime();
                Value ret = worker.call(mockContent);
                var costNs = System.nanoTime() + start;
                var costMs = costNs / 1000000;
                String code = ret.getArrayElement(0).asString();
                System.out.println("第" + (i+1) + "次调用, 时长: " + costNs + "ns, " + costMs + "ms, 结果: " + code);
            }
        };
    }
}
