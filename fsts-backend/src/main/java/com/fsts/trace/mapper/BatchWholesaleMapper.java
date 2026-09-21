package com.fsts.trace.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fsts.trace.entity.BatchWholesale;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 批发与冷链储运环节明细 Mapper。
 */
@Mapper
public interface BatchWholesaleMapper extends BaseMapper<BatchWholesale> {

    @Select("""
            <script>
            SELECT * FROM batch_wholesale WHERE batch_id IN
            <foreach collection="ids" item="id" open="(" separator="," close=")">#{id}</foreach>
            </script>
            """)
    List<BatchWholesale> selectByBatchIds(@Param("ids") List<Long> ids);
}
