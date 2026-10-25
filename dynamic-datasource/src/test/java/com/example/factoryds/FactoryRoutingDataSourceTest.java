package com.example.factoryds;

import java.util.ArrayList;
import java.util.List;

import javax.sql.DataSource;

import com.example.factoryds.annotation.FactoryDS;
import com.example.factoryds.aspect.FactoryDataSourceAspect;
import com.zaxxer.hikari.HikariDataSource;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 不依赖真实数据库:只验证"当前工厂编码 -> 选中哪个数据源"的路由决策,
 * 以及切面的写入/还原行为。HikariDataSource 无参构造不会真正建池。
 */
class FactoryRoutingDataSourceTest {

    private final FactoryRoutingDataSource routing = new FactoryRoutingDataSource();

    private final HikariDataSource f001 = stub("jdbc:h2:mem:f001");
    private final HikariDataSource f002 = stub("jdbc:h2:mem:f002");
    private final HikariDataSource f003 = stub("jdbc:h2:mem:f003");

    FactoryRoutingDataSourceTest() {
        routing.register("F001", f001);
        routing.register("F002", f002);
        routing.setDefaultDataSource(f001);
    }

    @AfterEach
    void cleanup() {
        FactoryContext.clear();
    }

    private static HikariDataSource stub(String url) {
        HikariDataSource ds = new HikariDataSource();
        ds.setJdbcUrl(url);
        return ds;
    }

    @Test
    void routesToFactoryOfCurrentContext() {
        FactoryContext.set("F002");
        assertThat(routing.determineTargetDataSource()).isSameAs(f002);
    }

    @Test
    void fallsBackToDefaultWhenContextMissing() {
        assertThat(routing.determineTargetDataSource()).isSameAs(f001);
    }

    @Test
    void fallsBackToDefaultForUnregisteredFactory() {
        FactoryContext.set("F999");
        assertThat(routing.determineTargetDataSource()).isSameAs(f001);
    }

    @Test
    void runtimeRegisterAndUnregister() {
        routing.register("F003", f003);
        assertThat(routing.contains("F003")).isTrue();
        FactoryContext.set("F003");
        assertThat(routing.determineTargetDataSource()).isSameAs(f003);

        routing.unregister("F003");
        assertThat(routing.contains("F003")).isFalse();
        assertThat(routing.determineTargetDataSource()).isSameAs(f001);
    }

    // ===== @FactoryDS 切面行为 =====

    static class OrderService {

        @FactoryDS("F002")
        public String fromAnnotation() {
            return FactoryContext.get();
        }

        public String current() {
            return FactoryContext.get();
        }
    }

    private OrderService proxied() {
        AspectJProxyFactory factory = new AspectJProxyFactory(new OrderService());
        factory.addAspect(new FactoryDataSourceAspect());
        return factory.getProxy();
    }

    @Test
    void aspectSwitchesFactoryInsideAnnotatedMethod() {
        FactoryContext.set("F001");
        assertThat(proxied().fromAnnotation()).isEqualTo("F002");
    }

    @Test
    void aspectRestoresOuterFactoryAfterMethod() {
        FactoryContext.set("F001");
        proxied().fromAnnotation();
        assertThat(FactoryContext.get()).isEqualTo("F001");
    }

    @Test
    void aspectClearsContextWhenNoOuterFactory() {
        proxied().fromAnnotation();
        assertThat(FactoryContext.get()).isNull();
    }

    @Test
    void aspectDoesNotTouchUnannotatedMethods() {
        FactoryContext.set("F002");
        assertThat(proxied().current()).isEqualTo("F002");
    }

    // ===== 类级注解兜底 =====

    @FactoryDS("F003")
    static class F003Service {

        public String current() {
            return FactoryContext.get();
        }
    }

    @Test
    void classLevelAnnotationAsDefault() {
        AspectJProxyFactory factory = new AspectJProxyFactory(new F003Service());
        factory.addAspect(new FactoryDataSourceAspect());
        F003Service proxy = factory.getProxy();
        assertThat(proxy.current()).isEqualTo("F003");
        assertThat(FactoryContext.get()).isNull();
    }
}
