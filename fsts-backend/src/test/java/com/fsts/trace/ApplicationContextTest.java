package com.fsts.trace;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * 上下文加载测试：不依赖 MySQL 与 Redis，验证以下"编译期无法发现"的问题。
 *
 * <p>1. Spring 容器的 Bean 装配是否完整（构造器注入缺失、循环依赖、条件装配冲突）；
 * 2. MyBatis-Plus 的 Mapper 扫描与 XML 映射文件能否正确解析（namespace、
 *    resultType、动态 SQL 标签写错都会在这里直接启动失败）；
 * 3. Jackson、拦截器、参数解析器、线程池、缓存等配置类能否正常初始化；
 * 4. 多套条件装配（Redis 开 / 关）的默认分支是否正确生效。
 *
 * <p>这里用 H2 的 MySQL 兼容模式替代真实 MySQL：
 * 只验证"框架装配"，业务流程与 SQL 语义仍需在真实 MySQL 上联调，
 * 二者不能互相替代。
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:fsts_context;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.datasource.hikari.maximum-pool-size=4",
        "spring.datasource.hikari.minimum-idle=1",
        "fsts.redis.enabled=false",
        "management.health.redis.enabled=false",
        "logging.level.root=warn",
        "mybatis-plus.configuration.log-impl=org.apache.ibatis.logging.nologging.NoLoggingImpl"
})
class ApplicationContextTest {

    @Test
    void contextLoads() {
        // 能走到这里即代表：Bean 装配成功 + Mapper 与 XML 解析通过
    }
}
