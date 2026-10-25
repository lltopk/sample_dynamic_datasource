package com.example.factoryds;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * application.yml 中工厂 -> 数据源的映射配置。
 *
 * <pre>
 * factory:
 *   datasource:
 *     default-factory: F001
 *     factories:
 *       F001:
 *         url: jdbc:mysql://127.0.0.1:3306/mes_f001
 *         username: root
 *         password: 123456
 *       F002:
 *         url: jdbc:mysql://127.0.0.1:3306/mes_f002
 *         username: root
 *         password: 123456
 * </pre>
 */
@ConfigurationProperties(prefix = "factory.datasource")
public class FactoryDataSourceProperties {

    /** 默认工厂编码:请求未携带工厂编码或编码未注册时,回落到该工厂的数据源 */
    private String defaultFactory;

    /** 工厂编码 -> 该工厂库的连接配置 */
    private Map<String, DataSourceConfig> factories = new LinkedHashMap<>();

    public String getDefaultFactory() {
        return defaultFactory;
    }

    public void setDefaultFactory(String defaultFactory) {
        this.defaultFactory = defaultFactory;
    }

    public Map<String, DataSourceConfig> getFactories() {
        return factories;
    }

    public void setFactories(Map<String, DataSourceConfig> factories) {
        this.factories = factories;
    }

    /**
     * 单个工厂的连接池配置。字段名故意与 Hikari 常用项对齐,
     * 需要更多池参数(如 connection-timeout)时在此扩展即可。
     */
    public static class DataSourceConfig {

        private String url;
        private String username;
        private String password;
        private String driverClassName;
        private int maximumPoolSize = 10;
        private int minimumIdle = 2;

        public String getUrl() {
            return url;
        }

        public void setUrl(String url) {
            this.url = url;
        }

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }

        public String getDriverClassName() {
            return driverClassName;
        }

        public void setDriverClassName(String driverClassName) {
            this.driverClassName = driverClassName;
        }

        public int getMaximumPoolSize() {
            return maximumPoolSize;
        }

        public void setMaximumPoolSize(int maximumPoolSize) {
            this.maximumPoolSize = maximumPoolSize;
        }

        public int getMinimumIdle() {
            return minimumIdle;
        }

        public void setMinimumIdle(int minimumIdle) {
            this.minimumIdle = minimumIdle;
        }
    }
}
