package com.example.factoryds.aspect;

import java.lang.reflect.Method;

import com.example.factoryds.FactoryContext;
import com.example.factoryds.annotation.FactoryDS;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 拦截 {@link FactoryDS},进入方法前把工厂编码写入线程上下文,退出时还原。
 *
 * <p>order 必须小于事务切面(@Transactional 默认 Ordered.LOWEST_PRECEDENCE)的原因:
 * 事务开启的瞬间,DataSourceTransactionManager 就会调用 getConnection() 拿到连接并
 * 绑定到当前线程 —— 那是路由唯一生效的时机。如果事务切面先执行,工厂编码还没写入,
 * 事务已经绑定了默认工厂的连接,切面再切换也无效。数据源切面必须处在更外层。
 */
@Aspect
@Component
@Order(Ordered.LOWEST_PRECEDENCE - 100)
public class FactoryDataSourceAspect {

    /**
     * 切点只负责"命中":方法或所属类上标了 @FactoryDS。
     * 具体 value 由 advice 内解析 —— 方法级优先,类级兜底。
     *
     * <p>刻意不写 "@annotation(factoryDS) || @within(factoryDS)" 的参数绑定形式:
     * {@code ||} 组合下两个分支各自绑定,Spring AOP 运行期可能从"未匹配的那一支"
     * 取绑定值,得到 null(本项目测试踩到过,见 FactoryRoutingDataSourceTest)。
     */
    @Around("@annotation(com.example.factoryds.annotation.FactoryDS)"
            + " || @within(com.example.factoryds.annotation.FactoryDS)")
    public Object switchFactory(ProceedingJoinPoint joinPoint) throws Throwable {
        FactoryDS factoryDS = resolveFactoryDS(joinPoint);
        String previous = FactoryContext.setAndReturnPrevious(factoryDS.value());
        try {
            return joinPoint.proceed();
        } finally {
            // 还原外层工厂而不是简单 clear:支持 @FactoryDS 嵌套(A 切到 F002,内部又调 B 切到 F003)
            FactoryContext.restore(previous);
        }
    }

    private FactoryDS resolveFactoryDS(ProceedingJoinPoint joinPoint) {
        Method method = ((MethodSignature) joinPoint.getSignature()).getMethod();
        FactoryDS factoryDS = AnnotatedElementUtils.findMergedAnnotation(method, FactoryDS.class);
        if (factoryDS == null) {
            factoryDS = AnnotatedElementUtils.findMergedAnnotation(
                    joinPoint.getTarget().getClass(), FactoryDS.class);
        }
        return factoryDS;
    }
}
