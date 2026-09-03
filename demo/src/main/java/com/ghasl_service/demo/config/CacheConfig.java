package com.ghasl_service.demo.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

/**
 * Cache configuration for Caffeine local cache.
 * Provides in-memory caching with automatic eviction policies.
 */
@Configuration
@EnableCaching
public class CacheConfig {
    
    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager();
        cacheManager.setCaffeine(Caffeine.newBuilder()
            .expireAfterWrite(15, TimeUnit.MINUTES) // 15-minute TTL
            .maximumSize(100) // Max 100 cached entries
            .recordStats()); // Enable cache statistics for monitoring
        return cacheManager;
    }
}
