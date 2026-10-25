package com.example.factoryds.async;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * @Async 线程池统一挂上 TaskDecorator:异步任务自动继承提交线程的工厂编码,
 * 业务侧的 @Async 方法里照常操作当前工厂数据库,无需手动透传。
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    @Bean("taskExecutor")
    public ThreadPoolTaskExecutor taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(200);
        executor.setThreadNamePrefix("factory-async-");
        // 提交时捕获工厂编码,执行时注入,执行完清理
        executor.setTaskDecorator(FactoryContextRunnable::of);
        return executor;
    }
}
