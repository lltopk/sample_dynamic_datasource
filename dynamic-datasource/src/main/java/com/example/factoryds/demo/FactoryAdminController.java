package com.example.factoryds.demo;

import java.util.Map;
import java.util.Set;

import com.example.factoryds.FactoryDataSourceManager;
import com.example.factoryds.FactoryDataSourceProperties;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 运行期动态上新工厂的演示入口。
 *
 * <pre>
 * POST /api/admin/factories
 * {
 *   "factoryCode": "F003",
 *   "config": {
 *     "url": "jdbc:mysql://127.0.0.1:3306/mes_f003",
 *     "username": "root",
 *     "password": "123456"
 *   }
 * }
 * </pre>
 *
 * 注册成功后立刻生效,前端带上 X-Factory-Code: F003 即可访问新工厂,全程无需重启。
 *
 * <p>!! 生产环境该接口必须挂权限校验(管理员角色 + 操作审计),
 * 否则等于开放了"任意人把流量导到任意数据库"的能力。
 */
@RestController
@RequestMapping("/api/admin/factories")
public class FactoryAdminController {

    private final FactoryDataSourceManager manager;

    public FactoryAdminController(FactoryDataSourceManager manager) {
        this.manager = manager;
    }

    public record RegisterFactoryRequest(String factoryCode,
                                         FactoryDataSourceProperties.DataSourceConfig config) {
    }

    @PostMapping
    public Map<String, Object> register(@RequestBody RegisterFactoryRequest request) {
        if (request.factoryCode() == null || request.factoryCode().isBlank()
                || request.config() == null || request.config().getUrl() == null) {
            throw new IllegalArgumentException("factoryCode 与 config.url 为必填项");
        }
        manager.register(request.factoryCode(), request.config());
        return Map.of("ok", true, "factoryCode", request.factoryCode());
    }

    @DeleteMapping("/{factoryCode}")
    public Map<String, Object> unregister(@PathVariable String factoryCode) {
        manager.unregister(factoryCode);
        return Map.of("ok", true, "factoryCode", factoryCode);
    }

    @GetMapping
    public Set<Object> list() {
        return manager.registeredFactories();
    }
}
