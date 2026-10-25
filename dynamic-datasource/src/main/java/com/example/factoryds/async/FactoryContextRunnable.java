package com.example.factoryds.async;

import com.example.factoryds.FactoryContext;

/**
 * Runnable 装饰器:捕获"提交任务那一刻"的工厂编码,带到执行线程里,执行完恢复现场。
 *
 * <p>为什么需要它:ThreadLocal 不会随线程切换自动传递。@Async、自建线程池、
 * CompletableFuture 里直接执行数据库操作时,工厂编码会丢失(读到 null,
 * 回落默认工厂 —— 而且是静默回落,不报错,非常隐蔽)。
 *
 * <pre>
 * // 手动包装单个任务
 * executor.submit(FactoryContextRunnable.of(() -> orderService.sync(id)));
 * </pre>
 *
 * 给 Spring 线程池统一配置见 {@link AsyncConfig}。
 */
public final class FactoryContextRunnable implements Runnable {

    private final Runnable delegate;
    private final String factoryCode;

    private FactoryContextRunnable(Runnable delegate, String factoryCode) {
        this.delegate = delegate;
        this.factoryCode = factoryCode;
    }

    public static FactoryContextRunnable of(Runnable delegate) {
        return new FactoryContextRunnable(delegate, FactoryContext.get());
    }

    @Override
    public void run() {
        if (factoryCode == null) {
            // 提交线程本身没有工厂上下文:清空执行线程可能残留的值,避免串厂
            FactoryContext.clear();
            try {
                delegate.run();
            } finally {
                FactoryContext.clear();
            }
            return;
        }
        String previous = FactoryContext.setAndReturnPrevious(factoryCode);
        try {
            delegate.run();
        } finally {
            FactoryContext.restore(previous);
        }
    }
}
