package com.zero.dynamic.invoker;

import lombok.Data;
import lombok.Getter;
import lombok.experimental.Accessors;

import javax.tools.*;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.net.URI;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Java 动态编译器
 *
 * @author Zero.
 * <p> Created on 2025/7/8 18:40 </p>
 */
public class JavaDynamicCompiler {
    /// 单例编译器
    private static volatile JavaDynamicCompiler instance;
    private static final Object LOCK = new Object();

    /// Java 编译器
    private final JavaCompiler compiler;
    /// 编译选项设置
    private final List<String> options;
    /// Class 缓存
    private final Map<String, Class<?>> classCache;
    /// Class 字节码缓存
    private final Map<String, byte[]> byteCodeCache;
    /// 类加载器
    private static final MemoryClassLoader classLoader = new MemoryClassLoader();

    public static JavaDynamicCompiler getInstance() {
        if (instance == null) {
            synchronized (LOCK) {
                if (instance == null) {
                    instance = new JavaDynamicCompiler();
                }
            }
        }
        return instance;
    }

    public JavaDynamicCompiler() {
        this.compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) throw new RuntimeException("The current environment does not support dynamic compilation");
        this.classCache = new ConcurrentHashMap<>();
        this.byteCodeCache = new ConcurrentHashMap<>();
        this.options = List.of("-target", "24", "-source", "24");
    }



    /// 编译结果
    @Data
    @Accessors(fluent = true)
    public static class CompileResult {
        /// 是否编译成功
        private final boolean success;
        /// 类名
        private final String className;
        /// 编译实例
        private final Class<?> prototype;
        /// 编译失败原因
        private final String message;
    }



    /// 动态编译类信息
    ///
    /// @param className 类限定名
    /// @param sourceCode 源码
    /// @return {@link Class} 类的Class对象
    public Class<?> compile(String className, String sourceCode) {
        CompileResult result = this.compile(className, sourceCode, true);
        if (result.success) return result.prototype;
        throw new RuntimeException(result.message);
//        try {
//            // step1 获取编译器
//            JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
//            if (compiler == null) {
//                throw new RuntimeException("The environment does not support dynamic compilation");
//            }
//            // step2 获取文件管理器
//            StandardJavaFileManager fileManager = compiler.getStandardFileManager(null, null, null);
//            MemoryClassFileManager classManager = new MemoryClassFileManager(fileManager);
//
//            // step3 创建Java源文件对象
//            JavaFileObject source = new JavaSourceFromString(className, classCode);
//            Iterable<? extends JavaFileObject> fileObjects = Collections.singletonList(source);
//
//            // step4 编译选项设置
//            List<String> options = List.of("-target", "24");
//
//            // step5 创建编译任务
//            JavaCompiler.CompilationTask task = compiler.getTask(null, classManager, null, options, null, fileObjects);
//            if (!task.call()) {
//                // 编译失败
//                throw new RuntimeException("compile task failed");
//            }
//            // 加载类，并且实例化
//            // TODO 为什么每次都要 new MemoryClassLoader
//            MemoryClassLoader classLoader = new MemoryClassLoader(classManager.getClassFiles());
//            Class<?> clazz = classLoader.loadClass(className);
//            return clazz.getDeclaredConstructor().newInstance();
//        } catch (Exception e) {
//            throw new RuntimeException("compile failed", e);
//        }
    }

    /// 编译源码
    ///
    /// @param className 类名
    /// @param sourceCode 源码
    /// @param cache 是否使用缓存
    /// @return {@link CompileResult} 编译结果
    public CompileResult compile(String className, String sourceCode, boolean cache) {
        // 是否已加载过，从缓存获取Class
        if (cache && classCache.containsKey(className)) {
            return new CompileResult(true, className, classCache.get(className), null);
        }
        try {
            // 编译 & 加载
            return this.doCompile(className, sourceCode, cache);
        }catch (Exception e) {
            return new CompileResult(false, className, null, e.getMessage());
        }
    }

    /// 执行编译
    /// @param className  类名
    /// @param sourceCode 源码
    /// @param cache      是否缓存机制
    private CompileResult doCompile(String className, String sourceCode, boolean cache) throws Exception {
        // step1: 获取Java文件管理器
        try(StandardJavaFileManager fileManager = compiler.getStandardFileManager(null, null, null)){
            // 构建自定义的内存源文件管理器，用于 JavaFileObject 的创建和保存
            MemoryClassFileManager classManager = new MemoryClassFileManager(fileManager);
            // step2 构建源文件对象
            JavaFileObject source = new JavaSourceFromString(className, sourceCode);
            Iterable<? extends JavaFileObject> fileObjects = Collections.singletonList(source);
            // step3 编译错误收集器
            DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();

            // step4: 创建编译任务
            JavaCompiler.CompilationTask task = this.compiler.getTask(null, classManager, diagnostics, options, null, fileObjects);
            if (task.call()) {
                // step5: 编译任务成功
                // 读取编译后的字节码，进行类加载、实例化。
                ByteCodeJavaFileObject classFile = classManager.getClassFiles().get(className);
                if (classFile == null) return new CompileResult(false, className, null, "No bytecode generated");
                // 获取源文件和字节码
                byte[] byteCode = classFile.getBytes();
                // 类加载, 获取Class对象
                Class<?> clazz = classLoader.defineClass(className, byteCode);
                if (cache) {
                    // 缓存类实例和字节码数组
                    classCache.put(className, clazz);
                    byteCodeCache.put(className, byteCode);
                }
                return new CompileResult(true, className, clazz, null);
            }else {
                // step6 编译失败，收集失败信息
                StringBuilder err = new StringBuilder();
                for (Diagnostic<? extends JavaFileObject> diagnostic : diagnostics.getDiagnostics()) {
                    err.append(diagnostic.toString()).append("\n");
                }
                return new CompileResult(false, className, null, err.toString());
            }

        }
    }

    /**
     * 源代码文件抽象
     * 将字符串形式的源码包装成 {@link JavaFileObject}, 以便可以被 JavaCompiler 直接编译.
     */
    static class JavaSourceFromString extends SimpleJavaFileObject {
        /**
         * 字符串源码
         */
        private final String sourceCode;

        protected JavaSourceFromString(String className, String sourceCode) {
            // 根据类名创建一个虚拟的 .java 文件, 让编译器认为它是一个合法的源码文件
            super(URI.create("string:///" + className.replace('.', '/') + Kind.SOURCE.extension), Kind.SOURCE);
            this.sourceCode = sourceCode;
        }

        /**
         * 获取源文件内容
         */
        @Override
        public CharSequence getCharContent(boolean ignoreEncodingErrors) {
            return sourceCode;
        }
    }

    /**
     * 表示编译`.java` 文件后的字节码内容
     */
    static class ByteCodeJavaFileObject extends SimpleJavaFileObject {
        private final ByteArrayOutputStream bos = new ByteArrayOutputStream();
        public ByteCodeJavaFileObject(String className) {
            super(URI.create("string:///" + className.replace('.', '/') + Kind.CLASS.extension), Kind.CLASS);
        }
        /**
         * 获取字节码输出流
         */
        @Override
        public OutputStream openOutputStream() {
            return bos;
        }
        /**
         * 获取字节码数组
         */
        public byte[] getBytes() {
            return bos.toByteArray();
        }
    }

    /**
     *
     * 内存类文件管理器：将动态编译的源码缓存在这里.
     */
    static class MemoryClassFileManager extends ForwardingJavaFileManager<JavaFileManager> {
        @Getter
        private final Map<String, ByteCodeJavaFileObject> classFiles = new HashMap<>();

        protected MemoryClassFileManager(JavaFileManager fileManager) {
            super(fileManager);
        }
        @Override
        public JavaFileObject getJavaFileForOutput(Location location, String className, JavaFileObject.Kind kind, FileObject sibling) throws IOException {
            if (kind == JavaFileObject.Kind.CLASS) {
                ByteCodeJavaFileObject classFile = new ByteCodeJavaFileObject(className);
                classFiles.put(className, classFile);
                return classFile;
            }
            return super.getJavaFileForOutput(location, className, kind, sibling);
        }
    }

    /**
     * 共享动态编译类加载器
     */
    static class MemoryClassLoader extends ClassLoader implements AutoCloseable {
        /// 已加载过的Class
        private final Map<String, Class<?>> classes = new ConcurrentHashMap<>();
        public Class<?> defineClass(String name, byte[] bytecode) {
            return classes.computeIfAbsent(name, k -> defineClass(k, bytecode, 0, bytecode.length));
        }
        @Override
        protected Class<?> findClass(String className) throws ClassNotFoundException {
            Class<?> clazz = classes.get(className);
            if (clazz == null) {
                throw new ClassNotFoundException(className);
            }
            return clazz;
        }
        @Override
        public void close() {
            classes.clear();
        }
    }

    public static void main(String[] args) throws Exception {
        JavaDynamicCompiler compiler = JavaDynamicCompiler.getInstance();
        String code = """
                public class HelloWorld {
                    public void sayHello() {
                        System.out.println("Hello from dynamic code!");
                    }
                }
                """;
        Class<?> clazz = compiler.compile("HelloWorld", code);
        Object instance = clazz.getDeclaredConstructor().newInstance();
        clazz.getMethod("sayHello").invoke(instance);


        String code2 = """
                package com.zero.dyn.app;
                public class Decode {
                    public static String decode(String content) {
                        System.out.printf("Hello %s \\n", content);
                        return "Hello " + content;
                    }
                }
                """;
        Class<?> decodeClass = compiler.compile("com.zero.dyn.app.Decode", code2);
        Method decodeMethod = decodeClass.getMethod("decode", String.class);
        Object value = decodeMethod.invoke(null, "Java code");
        System.out.println(value);
    }
}
