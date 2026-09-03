package com.ghasl_service.demo.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.concurrent.TimeUnit;

/**
 * Web configuration for static resource caching, HTTP headers, and rate limiting.
 * Implements long-term caching for static assets to reduce bandwidth.
 * Registers IP-based rate limiting interceptor for order submission endpoint.
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final OrderRateLimitInterceptor orderRateLimitInterceptor;

    @Autowired
    public WebConfig(OrderRateLimitInterceptor orderRateLimitInterceptor) {
        this.orderRateLimitInterceptor = orderRateLimitInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // Apply rate limiting specifically to order submission endpoint
        // This prevents DoS attacks on the unauthenticated POST /api/v1/orders endpoint
        registry.addInterceptor(orderRateLimitInterceptor)
                .addPathPatterns("/api/v1/orders");
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // Cache CSS, JS, and assets for 1 year (31536000 seconds)
        // These are versioned or have content hashes, so long caching is safe
        registry.addResourceHandler("/css/**")
                .addResourceLocations("classpath:/static/css/")
                .setCacheControl(CacheControl.maxAge(365, TimeUnit.DAYS).cachePublic());
        
        registry.addResourceHandler("/js/**")
                .addResourceLocations("classpath:/static/js/")
                .setCacheControl(CacheControl.maxAge(365, TimeUnit.DAYS).cachePublic());
        
        registry.addResourceHandler("/assets/**")
                .addResourceLocations("classpath:/static/assets/")
                .setCacheControl(CacheControl.maxAge(365, TimeUnit.DAYS).cachePublic());
        
        // Cache images for 1 year
        registry.addResourceHandler("/images/**")
                .addResourceLocations("classpath:/static/images/")
                .setCacheControl(CacheControl.maxAge(365, TimeUnit.DAYS).cachePublic());
    }
}
