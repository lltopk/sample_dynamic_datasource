package com.example.factoryds;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import javax.sql.DataSource;

import org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource;

/**
 * 按工厂编码路由的 DataSource —— 整个方案的核心。
 *
 * <p>原理:Spring 生态(JdbcTemplate / MyBatis / 事务管理器)每次需要连接时都调用
 * {@code dataSource.getConnection()}。父类 {@link AbstractRoutingDataSource} 把这个调用
 * 委托给 {@link #determineTargetDataSource()}:先通过
 * {@link #determineCurrentLookupKey()} 拿到当前线程的工厂编码,再从目标数据源表里
 * 取出该工厂对应的真实 DataSource,最终由它返回物理连接。
 *
 * <p>相对常见的"继承后调 super.afterPropertiesSet() 重建"写法,这里做了两点定制:
 * <ol>
 *   <li>resolve 结果是不可变快照 + volatile 发布:读路径({@code getConnection})
 *       完全无锁,运行期注册新工厂时不会让并发请求读到半初始化的 map;</li>
 *   <li>未注册的工厂编码 / 上下文缺失时,显式回落到默认数据源并打告警日志,
 *       便于排查"忘带 X-Factory-Code"这类问题。</li>
 * </ol>
 */
public class FactoryRoutingDataSource extends AbstractRoutingDataSource {

    /** 注册表:工厂编码 -> DataSource,运行期可变 */
    private final Map<Object, Object> targets = new ConcurrentHashMap<>();

    /** targets 解析后的只读快照,volatile 保证对并发读的安全发布 */
    private volatile Map<Object, DataSource> resolved = Collections.emptyMap();

    private volatile DataSource defaultDataSource;

    /**
     * 注册一个工厂数据源。工厂编码已存在时忽略,避免重复注册把连接池换掉、旧池泄漏。
     */
    public synchronized void register(String factoryCode, DataSource dataSource) {
        if (targets.containsKey(factoryCode)) {
            logger.warn("工厂 [" + factoryCode + "] 的数据源已存在,忽略重复注册");
            return;
        }
        targets.put(factoryCode, dataSource);
        rebuild();
    }

    /**
     * 注销工厂。HikariCP 的 close 是优雅关闭:在途 SQL 不受影响,连接归还后才真正关闭。
     */
    public synchronized void unregister(String factoryCode) {
        Object removed = targets.remove(factoryCode);
        if (removed != null) {
            closeQuietly((DataSource) removed);
        }
        rebuild();
    }

    public boolean contains(String factoryCode) {
        return resolved.containsKey(factoryCode);
    }

    /** 启动期按 yml 配置批量注册,只做一次全量 rebuild */
    public synchronized void registerAll(Map<String, DataSource> dataSources) {
        targets.putAll(dataSources);
        rebuild();
    }

    public Set<Object> registeredFactories() {
        return resolved.keySet();
    }

    /** 供自检端点使用:查询某个工厂当前路由到的真实数据源 */
    public DataSource getDataSource(String factoryCode) {
        return resolved.get(factoryCode);
    }

    public void setDefaultDataSource(DataSource defaultDataSource) {
        this.defaultDataSource = defaultDataSource;
    }

    @Override
    public void afterPropertiesSet() {
        rebuild();
    }

    /** 在锁内整体重建快照,重建完成后一次性替换引用(原子发布) */
    private void rebuild() {
        Map<Object, DataSource> snapshot = new HashMap<>(targets.size());
        targets.forEach((key, value) ->
                snapshot.put(resolveSpecifiedLookupKey(key), resolveSpecifiedDataSource(value)));
        this.resolved = Collections.unmodifiableMap(snapshot);
    }

    /** 路由 key:直接取当前线程的工厂编码,为空时回落默认数据源 */
    @Override
    protected Object determineCurrentLookupKey() {
        return FactoryContext.get();
    }

    @Override
    protected DataSource determineTargetDataSource() {
        DataSource fallback = this.defaultDataSource;
        Object key = determineCurrentLookupKey();
        if (key == null) {
            // 上下文没有工厂编码(比如定时任务直接跑):回落默认工厂
            return fallback;
        }
        DataSource dataSource = resolved.get(key);
        if (dataSource == null) {
            logger.warn("工厂编码 [" + key + "] 未注册任何数据源,已回落到默认数据源");
            return fallback;
        }
        return dataSource;
    }

    /** 应用关闭时释放所有工厂的连接池 */
    public void shutdown() {
        targets.values().forEach(value -> closeQuietly((DataSource) value));
    }

    private void closeQuietly(DataSource dataSource) {
        try {
            if (dataSource instanceof AutoCloseable closeable) {
                closeable.close();
            }
        } catch (Exception ex) {
            logger.warn("关闭工厂数据源失败: " + ex.getMessage());
        }
    }
}
