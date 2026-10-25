package com.example.factoryds;

import org.springframework.util.StringUtils;

/**
 * 当前工厂编码的线程上下文。
 *
 * <p>这是整个动态数据源的"开关":业务入口(拦截器 / 切面 / 编程式调用)
 * 把工厂编码写进来,SQL 执行时 {@link FactoryRoutingDataSource} 再读出去路由。
 *
 * <p>线程池会复用线程,任何写入路径都必须在 finally 里恢复/清理,
 * 否则上一个请求残留的工厂编码会把后续请求路由到错误的数据库(数据串厂)。
 */
public final class FactoryContext {

    private static final ThreadLocal<String> CURRENT_FACTORY = new ThreadLocal<>();

    private FactoryContext() {
    }

    public static void set(String factoryCode) {
        if (!StringUtils.hasText(factoryCode)) {
            throw new IllegalArgumentException("factoryCode 不能为空");
        }
        CURRENT_FACTORY.set(factoryCode.trim());
    }

    public static String get() {
        return CURRENT_FACTORY.get();
    }

    /**
     * 切换到新工厂并返回切换前的旧值,配合 {@link #restore(String)} 用于
     * "临时切换后还原现场"(典型场景:切面、跨工厂同步的嵌套调用)。
     */
    public static String setAndReturnPrevious(String factoryCode) {
        String previous = CURRENT_FACTORY.get();
        set(factoryCode);
        return previous;
    }

    public static void restore(String previous) {
        if (previous == null) {
            clear();
        } else {
            CURRENT_FACTORY.set(previous);
        }
    }

    public static void clear() {
        CURRENT_FACTORY.remove();
    }
}
