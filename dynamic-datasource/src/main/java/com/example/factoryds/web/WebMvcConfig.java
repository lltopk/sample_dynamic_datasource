package com.example.factoryds.web;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final FactoryCodeInterceptor factoryCodeInterceptor;

    public WebMvcConfig(FactoryCodeInterceptor factoryCodeInterceptor) {
        this.factoryCodeInterceptor = factoryCodeInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(factoryCodeInterceptor).addPathPatterns("/**");
    }
}
