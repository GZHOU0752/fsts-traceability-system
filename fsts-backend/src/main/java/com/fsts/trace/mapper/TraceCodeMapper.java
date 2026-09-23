package com.fsts.trace.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fsts.trace.entity.TraceCode;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * 溯源标识码 Mapper。
 */
@Mapper
public interface TraceCodeMapper extends BaseMapper<TraceCode> {

    /**
     * 按标识码查询，命中 uk_trace_code 唯一索引。
     */
    @Select("SELECT * FROM trace_code WHERE trace_code = #{traceCode}")
    TraceCode selectByTraceCode(@Param("traceCode") String traceCode);

    /**
     * 消费者端产品搜索：按产品名称模糊匹配仍有效的溯源标识码。
     */
    @Select("""
            SELECT * FROM trace_code
             WHERE status = 1 AND product_variety LIKE CONCAT('%', #{keyword}, '%')
             ORDER BY generate_time DESC
             LIMIT 50
            """)
    List<TraceCode> searchProducts(@Param("keyword") String keyword);

    /**
     * 累加查询次数（由 TraceQueryCounter 聚合后批量调用，不在读路径同步执行）。
     */
    @Update("""
            UPDATE trace_code
               SET query_count = query_count + #{delta}, last_query_time = #{lastQueryTime}, update_time = NOW()
             WHERE id = #{id}
            """)
    int addQueryCount(@Param("id") Long id, @Param("delta") long delta,
                      @Param("lastQueryTime") java.time.LocalDateTime lastQueryTime);

    /**
     * 批号下架时同步失效标识码（接口 10.7 业务规则 2）。
     */
    @Update("UPDATE trace_code SET status = 0, update_time = NOW() WHERE batch_id = #{batchId} AND status = 1")
    int invalidateByBatchId(@Param("batchId") Long batchId);
}
