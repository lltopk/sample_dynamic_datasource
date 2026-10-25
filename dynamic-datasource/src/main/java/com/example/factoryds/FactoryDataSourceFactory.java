package com.example.factoryds;

import javax.sql.DataSource;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 为单个工厂创建 Hikari 连接池。
 *
 * 抽成独立组件的原因:启动时按 yml 装配和运行期动态注册(新工厂上线)
 * 走同一份建池逻辑,池参数行为保持一致。
 */
@Component
public class FactoryDataSourceFactory {

    public DataSource create(String factoryCode, FactoryDataSourceProperties.DataSourceConfig config) {
        HikariConfig hikari = new HikariConfig();
        // 池名带工厂编码,排障时在日志 / 监控里一眼能看出是哪个工厂的池
        hikari.setPoolName("factory-" + factoryCode);
        hikari.setJdbcUrl(config.getUrl());
        hikari.setUsername(config.getUsername());
        hikari.setPassword(config.getPassword());
        if (StringUtils.hasText(config.getDriverClassName())) {
            hikari.setDriverClassName(config.getDriverClassName());
        }
        hikari.setMaximumPoolSize(config.getMaximumPoolSize());
        hikari.setMinimumIdle(config.getMinimumIdle());
        // 默认行为:建池时即尝试获取连接,配置错误在启动/注册阶段就暴露,而不是留到第一次查询
        hikari.setInitializationFailTimeout(1);
        return new HikariDataSource(hikari);
    }
}
