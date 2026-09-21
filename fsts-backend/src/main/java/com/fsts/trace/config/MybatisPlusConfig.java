package com.fsts.trace.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.BlockAttackInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis-Plus 插件配置。
 *
 * <p>性能与安全考量：
 * <ul>
 *   <li>分页插件设置单页上限 500，防止前端传 size=999999 拖垮数据库；</li>
 *   <li>开启 count 语句 join 优化，减少大表分页的无效 join；</li>
 *   <li>加入防全表更新/删除插件，避免漏写 where 条件造成生产事故。</li>
 * </ul>
 */
@Configuration
public class MybatisPlusConfig {

    /** 单页最大条数 */
    private static final long MAX_PAGE_SIZE = 500L;

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();

        // 防全表更新与删除（官方建议放在分页插件之前）
        interceptor.addInnerInterceptor(new BlockAttackInnerInterceptor());

        PaginationInnerInterceptor pagination = new PaginationInnerInterceptor(DbType.MYSQL);
        pagination.setMaxLimit(MAX_PAGE_SIZE);
        // 超过最大页后不循环跳转，直接返回空列表
        pagination.setOverflow(false);
        // 优化 count SQL 中不必要的 join
        pagination.setOptimizeJoin(true);
        interceptor.addInnerInterceptor(pagination);

        return interceptor;
    }
}
