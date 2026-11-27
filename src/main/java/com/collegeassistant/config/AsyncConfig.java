package com.collegeassistant.config;

import java.util.concurrent.Executor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
@EnableAsync
@EnableConfigurationProperties(AppProperties.class)
public class AsyncConfig {

    /** Small bounded pool: embedding is rate-limited by the provider, more threads would not help. */
    @Bean(name = "ingestionExecutor")
    public Executor ingestionExecutor() {
        ThreadPoolTaskExecutor ex = new ThreadPoolTaskExecutor();
        ex.setCorePoolSize(2);
        ex.setMaxPoolSize(2);
        ex.setQueueCapacity(50);
        ex.setThreadNamePrefix("ingest-");
        ex.initialize();
        return ex;
    }
}
