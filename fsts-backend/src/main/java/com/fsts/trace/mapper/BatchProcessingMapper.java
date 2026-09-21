package com.fsts.trace.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fsts.trace.entity.BatchProcessing;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 冷冻加工环节明细 Mapper。
 */
@Mapper
public interface BatchProcessingMapper extends BaseMapper<BatchProcessing> {

    @Select("""
            <script>
            SELECT * FROM batch_processing WHERE batch_id IN
            <foreach collection="ids" item="id" open="(" separator="," close=")">#{id}</foreach>
            </script>
            """)
    List<BatchProcessing> selectByBatchIds(@Param("ids") List<Long> ids);
}
