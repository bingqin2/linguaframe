package com.linguaframe.common.security;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class DemoAccessWebMvcConfiguration implements WebMvcConfigurer {

    private final DemoAccessInterceptor demoAccessInterceptor;

    public DemoAccessWebMvcConfiguration(DemoAccessInterceptor demoAccessInterceptor) {
        this.demoAccessInterceptor = demoAccessInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(demoAccessInterceptor)
                .addPathPatterns("/api/**")
                .excludePathPatterns("/api/demo-session", "/api/demo-session/**");
    }
}
