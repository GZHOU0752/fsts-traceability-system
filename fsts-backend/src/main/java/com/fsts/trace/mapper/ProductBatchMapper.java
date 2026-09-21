package com.fsts.trace.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fsts.trace.entity.ProductBatch;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 产品批号 Mapper。
 *
 * <p><b>并发控制核心</b>：所有状态流转都写成"带状态前置条件的条件更新"（CAS），
 * 依据影响行数判断是否成功。相比"先 SELECT 再 UPDATE"（存在竞态）或
 * "SELECT ... FOR UPDATE"（锁范围大、易死锁），CAS 只锁目标行、天然幂等，
 * 是批号状态机在高并发下不出现脏状态的关键。
 */
@Mapper
public interface ProductBatchMapper extends BaseMapper<ProductBatch> {

    /**
     * 发布：新建(1) -> 已确认/已发布(3)，仅捕捞与养殖企业使用。
     */
    @Update("""
            UPDATE product_batch
               SET batch_status = 3, publish_time = #{publishTime}, update_time = NOW()
             WHERE id = #{id} AND enterprise_id = #{enterpriseId}
               AND batch_status = 1 AND deleted = 0
            """)
    int publishIfNew(@Param("id") Long id, @Param("enterpriseId") Long enterpriseId,
                     @Param("publishTime") LocalDateTime publishTime);

    /**
     * 待确认：新建(1) -> 待确认(2)。
     */
    @Update("""
            UPDATE product_batch
               SET batch_status = 2, update_time = NOW()
             WHERE id = #{id} AND enterprise_id = #{enterpriseId}
               AND batch_status = 1 AND deleted = 0
            """)
    int markPendingIfNew(@Param("id") Long id, @Param("enterpriseId") Long enterpriseId);

    /**
     * 上游确认进场：待确认(2) -> 已确认(3)。
     */
    @Update("""
            UPDATE product_batch
               SET batch_status = 3, publish_time = #{publishTime}, update_time = NOW()
             WHERE id = #{id} AND batch_status = 2 AND deleted = 0
            """)
    int confirmIfPending(@Param("id") Long id, @Param("publishTime") LocalDateTime publishTime);

    /**
     * 上游拒绝进场：待确认(2) -> 新建(1)，允许下游修正后重发。
     */
    @Update("""
            UPDATE product_batch
               SET batch_status = 1, publish_time = NULL, update_time = NOW()
             WHERE id = #{id} AND batch_status = 2 AND deleted = 0
            """)
    int revertToNewIfPending(@Param("id") Long id);

    /**
     * 下架：已确认(3) -> 已下架(4)。
     */
    @Update("""
            UPDATE product_batch
               SET batch_status = 4, off_shelf_time = #{offShelfTime},
                   remark = COALESCE(#{remark}, remark), update_time = NOW()
             WHERE id = #{id} AND enterprise_id = #{enterpriseId}
               AND batch_status = 3 AND deleted = 0
            """)
    int offShelfIfConfirmed(@Param("id") Long id, @Param("enterpriseId") Long enterpriseId,
                            @Param("offShelfTime") LocalDateTime offShelfTime,
                            @Param("remark") String remark);

    /**
     * 物理删除：仅允许删除"新建(1)"状态的本企业批号（接口 10.5）。
     * 明细 / 确认请求 / 溯源码由外键 ON DELETE CASCADE 同步清理。
     */
    @Update("DELETE FROM product_batch WHERE id = #{id} AND enterprise_id = #{enterpriseId} AND batch_status = 1")
    int deleteIfNew(@Param("id") Long id, @Param("enterpriseId") Long enterpriseId);

    /**
     * 溯源链查询（接口 12.1 的核心）：由零售商批号沿 upstream_batch_id 向上递归至源头。
     *
     * <p>深度固定不超过 4 层（捕捞与养殖 -> 加工 -> 批发 -> 零售），
     * 每层均走主键索引，单次查询代价极低；结果按环节升序返回。
     */
    @Select("""
            WITH RECURSIVE chain AS (
                SELECT b.*, 1 AS depth
                  FROM product_batch b
                 WHERE b.id = #{batchId} AND b.deleted = 0
                UNION ALL
                SELECT p.*, c.depth + 1
                  FROM product_batch p
                  JOIN chain c ON p.id = c.upstream_batch_id
                 WHERE p.deleted = 0 AND c.depth < 8
            )
            SELECT * FROM chain ORDER BY enterprise_type ASC
            """)
    List<ProductBatch> selectTraceChain(@Param("batchId") Long batchId);

    /**
     * 批量查询批号（消费者端组装明细用，避免 N+1 查询）。
     */
    @Select("""
            <script>
            SELECT * FROM product_batch
             WHERE deleted = 0 AND id IN
             <foreach collection="ids" item="id" open="(" separator="," close=")">#{id}</foreach>
            </script>
            """)
    List<ProductBatch> selectByIds(@Param("ids") List<Long> ids);
}
