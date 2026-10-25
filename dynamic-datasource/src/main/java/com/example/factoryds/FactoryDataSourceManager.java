package com.example.factoryds;

import java.util.Set;

import org.springframework.stereotype.Component;

/**
 * 工厂数据源的运维入口:应用不重启,新工厂上线/下线即注册即生效。
 *
 * <p>典型接入方式(选一):
 * <ul>
 *   <li>管理端点手动注册(见 demo.FactoryAdminController);</li>
 *   <li>应用启动后从主库的"工厂清单表"或配置中心(Nacos / Apollo)拉取,
 *       并监听变更调用 register / unregister —— 工厂与库的映射完全数据驱动。</li>
 * </ul>
 */
@Component
public class FactoryDataSourceManager {

    private final FactoryRoutingDataSource routingDataSource;
    private final FactoryDataSourceFactory dataSourceFactory;

    public FactoryDataSourceManager(FactoryRoutingDataSource routingDataSource,
                                    FactoryDataSourceFactory dataSourceFactory) {
        this.routingDataSource = routingDataSource;
        this.dataSourceFactory = dataSourceFactory;
    }

    /** 注册后立刻生效:下一个带该工厂编码的请求就会路由到新库 */
    public void register(String factoryCode, FactoryDataSourceProperties.DataSourceConfig config) {
        routingDataSource.register(factoryCode, dataSourceFactory.create(factoryCode, config));
    }

    public void unregister(String factoryCode) {
        routingDataSource.unregister(factoryCode);
    }

    public Set<Object> registeredFactories() {
        return routingDataSource.registeredFactories();
    }

    public boolean isRegistered(String factoryCode) {
        return routingDataSource.contains(factoryCode);
    }
}
