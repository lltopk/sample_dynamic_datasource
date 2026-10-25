package com.example.factoryds.web;

import com.example.factoryds.FactoryContext;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 最常用的切换入口:前端每个请求通过 Header 声明当前操作的工厂,
 * 业务代码完全无感,照常写 SQL 即可。
 *
 * <p>这里从 Header 取编码只是一种取法,按你的登录体系也可以改成:
 * {@code LoginUser user = SessionHolder.get(); FactoryContext.set(user.getFactoryCode());}
 * —— 重点是"在请求最外层写入,在请求结束时清理"。
 */
@Component
public class FactoryCodeInterceptor implements HandlerInterceptor {

    public static final String HEADER_NAME = "X-Factory-Code";

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String factoryCode = request.getHeader(HEADER_NAME);
        if (StringUtils.hasText(factoryCode)) {
            FactoryContext.set(factoryCode);
        } else {
            // 无条件覆盖:线程被 Tomcat 复用,绝不能沿用上一个请求残留的工厂
            FactoryContext.clear();
        }
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        // 双保险:即使 preHandle 之后有人手动 set 过,也保证归还线程前清理干净
        FactoryContext.clear();
    }
}
