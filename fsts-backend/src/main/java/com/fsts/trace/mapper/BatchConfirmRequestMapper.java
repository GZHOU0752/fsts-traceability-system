package com.fsts.trace.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.fsts.trace.dto.query.ConfirmRequestQuery;
import com.fsts.trace.entity.BatchConfirmRequest;
import com.fsts.trace.vo.ConfirmRequestListItemVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

/**
 * 进场确认请求 Mapper。
 */
@Mapper
public interface BatchConfirmRequestMapper extends BaseMapper<BatchConfirmRequest> {

    /**
     * 待处理请求分页（接口 11.1）。
     *
     * <p>SQL 写在 XML 中：需要 join 企业表补充"下游企业类型"，
     * 且 where 条件全部命中 {@code idx_request_to_enterprise} 等既有索引。
     */
    IPage<ConfirmRequestListItemVO> selectRequestPage(IPage<ConfirmRequestListItemVO> page,
                                                      @Param("q") ConfirmRequestQuery query,
                                                      @Param("toEnterpriseId") Long toEnterpriseId);

    /**
     * 并发安全的确认：只有"待确认"状态才能被更新为"已确认"。
     */
    @Update("""
            UPDATE batch_confirm_request
               SET request_status = 2, handle_time = #{handleTime}, handle_remark = #{handleRemark}, update_time = NOW()
             WHERE id = #{id} AND to_enterprise_id = #{toEnterpriseId} AND request_status = 1
            """)
    int confirmIfPending(@Param("id") Long id, @Param("toEnterpriseId") Long toEnterpriseId,
                         @Param("handleTime") LocalDateTime handleTime,
                         @Param("handleRemark") String handleRemark);

    /**
     * 并发安全的拒绝：只有"待确认"状态才能被更新为"已拒绝"。
     */
    @Update("""
            UPDATE batch_confirm_request
               SET request_status = 3, handle_time = #{handleTime}, handle_remark = #{handleRemark}, update_time = NOW()
             WHERE id = #{id} AND to_enterprise_id = #{toEnterpriseId} AND request_status = 1
            """)
    int rejectIfPending(@Param("id") Long id, @Param("toEnterpriseId") Long toEnterpriseId,
                        @Param("handleTime") LocalDateTime handleTime,
                        @Param("handleRemark") String handleRemark);

    /**
     * 被拒绝后重新发起：复用原记录，重置为待确认（uk_request_batch 保证一批一号一请求）。
     */
    @Update("""
            UPDATE batch_confirm_request
               SET request_status = 1, request_time = #{requestTime}, handle_time = NULL,
                   handle_remark = NULL, upstream_batch_id = #{upstreamBatchId},
                   upstream_batch_no = #{upstreamBatchNo}, handover_temp = #{handoverTemp},
                   update_time = NOW()
             WHERE id = #{id} AND request_status = 3
            """)
    int reopenRejected(@Param("id") Long id,
                       @Param("requestTime") LocalDateTime requestTime,
                       @Param("upstreamBatchId") Long upstreamBatchId,
                       @Param("upstreamBatchNo") String upstreamBatchNo,
                       @Param("handoverTemp") java.math.BigDecimal handoverTemp);

    /**
     * 更新已存在请求的上游信息（接口 10.4 业务规则 3）。
     */
    @Update("""
            UPDATE batch_confirm_request
               SET upstream_batch_id = #{upstreamBatchId}, upstream_batch_no = #{upstreamBatchNo},
                   handover_temp = #{handoverTemp}, update_time = NOW()
             WHERE id = #{id}
            """)
    int refreshUpstream(@Param("id") Long id,
                        @Param("upstreamBatchId") Long upstreamBatchId,
                        @Param("upstreamBatchNo") String upstreamBatchNo,
                        @Param("handoverTemp") java.math.BigDecimal handoverTemp);
}
