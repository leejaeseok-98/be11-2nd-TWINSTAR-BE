package com.TwinStar.TwinStar.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

@Configuration
@EnableAsync
public class AsyncConfig {

    @Bean(name = "imageUploadExecutor")
    public Executor imageUploadExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        // 코어 스레드 수: 평소 대기하는 스레드 개수
        executor.setCorePoolSize(10);
        // 최대 스레드 수: 요청이 몰릴 때 생성할 수 있는 최대 스레드 개수
        executor.setMaxPoolSize(50);
        // 대기 큐 용량: 스레드가 모두 바쁠 때 대기할 요청 수
        executor.setQueueCapacity(100);
        // 스레드 이름 접두사
        executor.setThreadNamePrefix("S3-Upload-");
        // 큐가 꽉 찼을 때 처리 전략: 호출한 스레드(Main Thread)가 직접 처리 (속도 조절 효과)
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }
}
