package com.cloudzone.shopbackend.config;

import java.util.concurrent.Executor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Enables {@code @Async} and names the pool that out-of-band work (notification email,
 * push fan-out) runs on.
 *
 * <p>A dedicated {@code notificationExecutor} is used rather than the default
 * {@code applicationTaskExecutor} so a burst of outbound mail can't starve the request
 * threads — and so the queue depth here is a knob we can tune independently.
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    /**
     * Small fixed pool: outbound mail is I/O-bound and rate-limited by the SMTP server
     * anyway, so a large pool only risks tripping provider limits. Caller-runs policy on
     * saturation degrades to synchronous send rather than dropping the notification.
     */
    @Bean(name = "notificationExecutor")
    public Executor notificationExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("notify-");
        executor.initialize();
        return executor;
    }
}
