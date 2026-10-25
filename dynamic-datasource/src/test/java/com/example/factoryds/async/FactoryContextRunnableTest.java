package com.example.factoryds.async;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

import com.example.factoryds.FactoryContext;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 手动包装用法 FactoryContextRunnable.of(task) 的行为验证。
 *
 * 每个用例都对应一个真实业务场景,先看懂这里,再看业务里怎么包任务。
 */
class FactoryContextRunnableTest {

    @AfterEach
    void cleanup() {
        FactoryContext.clear();
    }

    /**
     * 场景(反面教材):任务没有包装,直接丢给别的线程执行。
     * 结果:异步线程读到的工厂编码是 null —— 提交线程 set 的 "F002" 跟不过去。
     * 这就是"异步里查了默认工厂的库还不报错"的根源。
     */
    @Test
    void bareTaskLosesFactoryCodeAcrossThreads() throws Exception {
        FactoryContext.set("F002");
        try {
            ExecutorService pool = Executors.newSingleThreadExecutor();
            try {
                // 未包装的任务:裸读当前线程的工厂编码
                String seenInPoolThread = pool.submit(FactoryContext::get).get();
                assertThat(seenInPoolThread).isNull();
            } finally {
                pool.shutdownNow();
            }
        } finally {
            FactoryContext.clear();
        }
    }

    /**
     * 正确用法一:提交前用 of() 包装,异步线程里就能读到提交时刻的工厂编码。
     *
     * 业务里对应:
     *   executor.submit(FactoryContextRunnable.of(() -> orderService.sync(orderId)));
     */
    @Test
    void wrappedTaskCarriesFactoryCodeIntoPoolThread() throws Exception {
        FactoryContext.set("F002");
        try {
            ExecutorService pool = Executors.newSingleThreadExecutor();
            try {
                AtomicReference<String> seen = new AtomicReference<>("(任务未执行)");
                pool.submit(FactoryContextRunnable.of(() -> seen.set(FactoryContext.get()))).get();
                assertThat(seen.get()).isEqualTo("F002");
            } finally {
                pool.shutdownNow();
            }
        } finally {
            FactoryContext.clear();
        }
    }

    /**
     * 包装任务执行完,池线程不残留工厂编码。
     * 模拟线程池复用:同一个池线程紧接着执行下一个(未包装的)任务,
     * 读到的必须是 null 而不是上一个任务的 "F003" —— 否则就串厂了。
     */
    @Test
    void poolThreadDoesNotLeakFactoryCodeAfterTask() throws Exception {
        FactoryContext.set("F003");
        try {
            ExecutorService pool = Executors.newSingleThreadExecutor();
            try {
                AtomicReference<String> seen = new AtomicReference<>("(任务未执行)");
                pool.submit(FactoryContextRunnable.of(() -> seen.set(FactoryContext.get()))).get();
                assertThat(seen.get()).isEqualTo("F003");

                // 同一个线程立刻执行下一个裸任务:必须读不到 F003
                String after = pool.submit(FactoryContext::get).get();
                assertThat(after).isNull();
            } finally {
                pool.shutdownNow();
            }
        } finally {
            FactoryContext.clear();
        }
    }

    /**
     * 提交线程自己没有工厂编码(未 set)时,包装任务会清掉执行线程上的脏值,
     * 而不是沿用池线程上残留的历史编码 —— 防止"串到上一个请求的工厂"。
     */
    @Test
    void dirtyPoolThreadIsSanitizedWhenSubmitterHasNoContext() throws Exception {
        ExecutorService pool = Executors.newSingleThreadExecutor();
        try {
            // 先把池线程弄脏:直接提交一个裸任务,在里面 set 一个错误的编码
            pool.submit(() -> FactoryContext.set("F999")).get();

            // 提交线程此刻没有上下文(测试的 @AfterEach 尚未执行,当前值确为 null)
            assertThat(FactoryContext.get()).isNull();

            AtomicReference<String> seen = new AtomicReference<>("(任务未执行)");
            pool.submit(FactoryContextRunnable.of(() -> seen.set(FactoryContext.get()))).get();
            assertThat(seen.get()).isNull();   // 是 null,不是 F999
        } finally {
            pool.shutdownNow();
        }
    }
}
