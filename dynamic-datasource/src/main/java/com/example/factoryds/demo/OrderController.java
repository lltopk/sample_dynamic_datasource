package com.example.factoryds.demo;

import java.util.List;
import java.util.Map;

import javax.sql.DataSource;

import com.example.factoryds.FactoryContext;
import com.example.factoryds.FactoryRoutingDataSource;
import com.example.factoryds.annotation.FactoryDS;
import com.zaxxer.hikari.HikariDataSource;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 演示三种工厂切换方式(示例表 t_order,换成你项目里的表即可):
 *
 * <pre>
 * # 方式1:请求头切换 —— 查 F002 的库,业务代码无感
 * curl -H "X-Factory-Code: F002" http://localhost:8080/api/orders/1
 *
 * # 方式2:注解切换 —— 固定查 F001
 * curl http://localhost:8080/api/orders/f001/1
 *
 * # 方式3:编程式切换 —— 临时去某个工厂统计再切回来
 * curl http://localhost:8080/api/orders/count-in/F002
 *
 * # 自检:确认当前请求实际会路由到哪个库
 * curl -H "X-Factory-Code: F002" http://localhost:8080/api/orders/routing-info
 * </pre>
 */
@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final JdbcTemplate jdbcTemplate;
    private final FactoryRoutingDataSource routingDataSource;

    public OrderController(JdbcTemplate jdbcTemplate, FactoryRoutingDataSource routingDataSource) {
        this.jdbcTemplate = jdbcTemplate;
        this.routingDataSource = routingDataSource;
    }

    /** 方式1:工厂由请求头 X-Factory-Code 决定 */
    @GetMapping("/{id}")
    public List<Map<String, Object>> getById(@PathVariable long id) {
        return jdbcTemplate.queryForList("select * from t_order where id = ?", id);
    }

    /** 方式2:@FactoryDS 显式指定(方法级优先于类级) */
    @FactoryDS("F001")
    @GetMapping("/f001/{id}")
    public List<Map<String, Object>> fromF001(@PathVariable long id) {
        return jdbcTemplate.queryForList("select * from t_order where id = ?", id);
    }

    /** 方式3:方法内临时切换 —— 切过去查完,finally 里还原外层工厂 */
    @GetMapping("/count-in/{factoryCode}")
    public Map<String, Object> countIn(@PathVariable String factoryCode) {
        String previous = FactoryContext.setAndReturnPrevious(factoryCode);
        try {
            Long count = jdbcTemplate.queryForObject("select count(*) from t_order", Long.class);
            return Map.of("factory", factoryCode, "count", count == null ? 0 : count);
        } finally {
            FactoryContext.restore(previous);
        }
    }

    /** 自检端点:排查"到底路由到了哪个库"时最直观的一手证据 */
    @GetMapping("/routing-info")
    public Map<String, Object> routingInfo() {
        String factoryCode = FactoryContext.get();
        DataSource target = factoryCode == null ? null : routingDataSource.getDataSource(factoryCode);

        String routedTo;
        if (target instanceof HikariDataSource hikari) {
            routedTo = hikari.getJdbcUrl();
        } else if (target != null) {
            routedTo = target.toString();
        } else {
            routedTo = "(工厂编码缺失或未注册,回落默认数据源)";
        }
        return Map.of(
                "currentFactoryCode", factoryCode == null ? "(未设置)" : factoryCode,
                "routedTo", routedTo);
    }
}
