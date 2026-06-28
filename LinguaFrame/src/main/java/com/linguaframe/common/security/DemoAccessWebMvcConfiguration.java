package com.linguaframe.common.security;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class DemoAccessWebMvcConfiguration implements WebMvcConfigurer {

    private final DemoAccessInterceptor demoAccessInterceptor;
    private final LocalAuthInterceptor localAuthInterceptor;

    public DemoAccessWebMvcConfiguration(
            DemoAccessInterceptor demoAccessInterceptor,
            LocalAuthInterceptor localAuthInterceptor
    ) {
        this.demoAccessInterceptor = demoAccessInterceptor;
        this.localAuthInterceptor = localAuthInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(demoAccessInterceptor)
                .addPathPatterns("/api/**")
                .excludePathPatterns("/api/demo-session", "/api/demo-session/**", "/api/auth", "/api/auth/**");
        registry.addInterceptor(localAuthInterceptor)
                .addPathPatterns("/api/**")
                .excludePathPatterns("/api/demo-session", "/api/demo-session/**", "/api/auth", "/api/auth/**");
    }
}
