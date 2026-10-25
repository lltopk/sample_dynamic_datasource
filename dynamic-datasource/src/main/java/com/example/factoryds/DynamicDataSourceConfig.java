package com.example.factoryds;

import java.util.LinkedHashMap;
import java.util.Map;

import javax.sql.DataSource;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 装配入口:把 yml 里的每个工厂各建一个 Hikari 池,
 * 挂到 {@link FactoryRoutingDataSource} 下,并以它作为全局唯一的 DataSource bean。
 *
 * <p>容器里有了自定义 DataSource 后,Spring Boot 的 DataSourceAutoConfiguration
 * 会自动退让,不需要手工排除;JdbcTemplate / MyBatis / MyBatis-Plus /
 * DataSourceTransactionManager 都会天然拿到这个"路由数据源"。
 */
@Configuration
@EnableConfigurationProperties(FactoryDataSourceProperties.class)
public class DynamicDataSourceConfig {

    @Bean(destroyMethod = "shutdown")
    public FactoryRoutingDataSource dataSource(FactoryDataSourceProperties properties,
                                               FactoryDataSourceFactory factory) {
        Map<String, DataSource> targets = new LinkedHashMap<>();
        properties.getFactories().forEach((code, config) ->
                targets.put(code, factory.create(code, config)));
        if (targets.isEmpty()) {
            throw new IllegalStateException(
                    "factory.datasource.factories 至少需要配置一个工厂,例如 factory.datasource.factories.F001.url=...");
        }

        FactoryRoutingDataSource routing = new FactoryRoutingDataSource();
        routing.registerAll(targets);

        // 默认工厂:显式配置优先;未配置则取第一个,保证"上下文缺失"时系统仍可用
        String defaultFactory = properties.getDefaultFactory();
        DataSource defaultDataSource = defaultFactory != null && targets.containsKey(defaultFactory)
                ? targets.get(defaultFactory)
                : targets.values().iterator().next();
        routing.setDefaultDataSource(defaultDataSource);
        return routing;
    }
}
