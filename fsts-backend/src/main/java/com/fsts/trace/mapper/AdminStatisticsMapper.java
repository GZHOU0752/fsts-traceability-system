package com.fsts.trace.mapper;

import com.fsts.trace.dto.stat.BusinessMetricRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

/**
 * 大屏业务指标聚合（接口 7.6）。
 *
 * <p>企业维度的聚合在 {@link NodeEnterpriseMapper}（同一张表多条 SQL），
 * 这里跨 product_batch / batch_confirm_request / trace_code 三张表，单独成 Mapper 更清晰。
 */
@Mapper
public interface AdminStatisticsMapper {

    /**
     * 业务指标：批号总量、在架量、冷链异常量、待确认请求量、有效溯源码量。
     *
     * <p>五个子查询都命中既有索引（idx_batch_status / idx_request_status 等），
     * 数据量增长后可改为物化统计表或加 30 秒缓存（见 AdminStatisticsService）。
     */
    @Select("""
            SELECT
              (SELECT COUNT(*) FROM product_batch WHERE deleted = 0)                       AS total_batch_count,
              (SELECT COUNT(*) FROM product_batch WHERE deleted = 0 AND batch_status = 3)  AS active_batch_count,
              (SELECT COUNT(*) FROM product_batch WHERE deleted = 0 AND cold_chain_ok = 0) AS cold_chain_abnormal_count,
              (SELECT COUNT(*) FROM batch_confirm_request WHERE request_status = 1)        AS pending_confirm_count,
              (SELECT COUNT(*) FROM trace_code WHERE status = 1)                           AS valid_trace_code_count
            """)
    BusinessMetricRow selectBusinessMetrics();
}
