package com.example.factoryds.async;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import com.example.factoryds.FactoryContext;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * AsyncConfig(线程池统一挂 TaskDecorator)这条路线的行为验证。
 *
 * 业务代码只需要在方法上标 @Async,工厂编码自动跟过去,方法内照常操作数据库。
 */
@SpringJUnitConfig(AsyncConfigTest.Config.class)
class AsyncConfigTest {

    @Autowired
    private DemoService demoService;

    /** 模拟业务 Service:方法跑在 factory-async- 池线程上 */
    @Configuration
    @Import(AsyncConfig.class)
    static class Config {

        @Bean
        DemoService demoService() {
            return new DemoService();
        }
    }

    static class DemoService {

        /**
         * 调用方请求线程上工厂编码是 "F002"(拦截器已 set),
         * 方法体却在异步线程执行 —— 靠 TaskDecorator 把编码带过来。
         */
        @Async("taskExecutor")
        public CompletableFuture<String> currentFactory() {
            return CompletableFuture.completedFuture(FactoryContext.get());
        }
    }

    @AfterEach
    void cleanup() {
        FactoryContext.clear();
    }

    /**
     * 完整链路:调用线程 set 编码 → @Async 代理提交到 taskExecutor →
     * TaskDecorator(即 FactoryContextRunnable.of)捕获并注入 → 方法体读到。
     *
     * 业务等价写法:
     *   curl -H "X-Factory-Code: F002" ... 后触发 demoService.currentFactory()
     */
    @Test
    void asyncMethodSeesCallersFactoryCode() throws Exception {
        FactoryContext.set("F002");
        try {
            String seenInAsyncThread = demoService.currentFactory().get(3, TimeUnit.SECONDS);
            assertThat(seenInAsyncThread).isEqualTo("F002");
        } finally {
            FactoryContext.clear();
        }
    }

    /**
     * 直接验证 AsyncConfig 里那一行 setTaskDecorator(FactoryContextRunnable::of):
     * 只要任务经过这个池,无论谁提交,工厂编码都能带过去 —— 业务侧无需逐个包装。
     */
    @Test
    void taskDecoratorPropagatesFactoryCodeToPoolThread() throws Exception {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setTaskDecorator(FactoryContextRunnable::of);
        executor.initialize();
        try {
            FactoryContext.set("F003");
            String seen = executor.submit(FactoryContext::get).get();
            assertThat(seen).isEqualTo("F003");
        } finally {
            FactoryContext.clear();
            executor.shutdown();
        }
    }
}
