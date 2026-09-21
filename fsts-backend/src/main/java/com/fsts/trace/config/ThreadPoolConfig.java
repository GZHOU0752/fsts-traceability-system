package com.fsts.trace.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 线程池配置。
 *
 * <p>关键取舍：拒绝策略使用 {@link ThreadPoolExecutor.CallerRunsPolicy}（背压），
 * 队列满时由调用线程执行，从而自然降低入口流量，而不是静默丢弃任务。
 * 对于溯源查询次数这类"可丢"的统计任务，服务层改用主动降级（见 TraceQueryCounter）。
 */
@Configuration
public class ThreadPoolConfig {

    public static final String ASYNC_EXECUTOR = "fstsAsyncExecutor";

    @Bean(name = ASYNC_EXECUTOR)
    public Executor asyncExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        int cores = Runtime.getRuntime().availableProcessors();
        executor.setCorePoolSize(Math.max(8, cores * 2));
        executor.setMaxPoolSize(Math.max(32, cores * 8));
        executor.setQueueCapacity(2000);
        executor.setKeepAliveSeconds(120);
        executor.setThreadNamePrefix("fsts-async-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(20);
        executor.initialize();
        return executor;
    }

    @Bean
    public ThreadPoolTaskScheduler taskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(2);
        scheduler.setThreadNamePrefix("fsts-sched-");
        scheduler.setWaitForTasksToCompleteOnShutdown(true);
        scheduler.setAwaitTerminationSeconds(20);
        scheduler.setRemoveOnCancelPolicy(true);
        scheduler.initialize();
        return scheduler;
    }
}
