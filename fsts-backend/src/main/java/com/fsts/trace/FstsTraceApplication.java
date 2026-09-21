package com.fsts.trace;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 冷冻海产品溯源系统 - 后端启动类。
 *
 * <p>可用性说明：
 * <ul>
 *   <li>服务本身无状态（JWT + 无 Session），可多实例水平扩展；</li>
 *   <li>本地缓存 / Redis 均为可降级组件，Redis 不可用时自动退回本地缓存，不影响主流程；</li>
 *   <li>配合 {@code server.shutdown=graceful} 实现优雅停机，滚动发布不丢请求。</li>
 * </ul>
 *
 * @author FSTS
 */
@EnableAsync
@EnableScheduling
@MapperScan("com.fsts.trace.mapper")
@SpringBootApplication
public class FstsTraceApplication {

    public static void main(String[] args) {
        SpringApplication.run(FstsTraceApplication.class, args);
    }
}
