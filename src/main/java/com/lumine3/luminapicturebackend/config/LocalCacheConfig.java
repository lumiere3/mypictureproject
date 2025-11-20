package com.lumine3.luminapicturebackend.config;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
public class LocalCacheConfig {

    @Bean
   public Cache<String, String> localCacheByCaffeine() {
       return Caffeine.newBuilder()
               .initialCapacity(1024)
               .maximumSize(10_000L)
               .expireAfterWrite(Duration.ofMinutes(5))
               .build();
   }

   public void cleanLocalCache(){
        localCacheByCaffeine().invalidateAll();
   }
}
