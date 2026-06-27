package com.linguaframe.common.ratelimit;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class UploadRateLimitWebMvcConfiguration implements WebMvcConfigurer {

    private final UploadRateLimitInterceptor uploadRateLimitInterceptor;

    public UploadRateLimitWebMvcConfiguration(UploadRateLimitInterceptor uploadRateLimitInterceptor) {
        this.uploadRateLimitInterceptor = uploadRateLimitInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(uploadRateLimitInterceptor)
                .addPathPatterns("/api/media/uploads", "/api/media/uploads/validate");
    }
}
