package com.example.factoryds.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 声明该方法 / 类内的数据库操作固定路由到某个工厂。
 *
 * <pre>
 * // 显式指定工厂
 * {@literal @}FactoryDS("F002")
 * public Order getOrderFromF002(Long id) { ... }
 *
 * // 整个 Service 默认走 F001(方法上的注解优先级更高)
 * {@literal @}FactoryDS("F001")
 * {@literal @}Service
 * public class F001ReportService { ... }
 * </pre>
 *
 * <p>用法优先级:请求头 X-Factory-Code(全局兜底)&lt; 类上注解 &lt; 方法上注解。
 * <b>同类内部自调用(this.xxx())不经过代理,注解不会生效。</b>
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface FactoryDS {

    /** 工厂编码,如 F001 */
    String value();
}
