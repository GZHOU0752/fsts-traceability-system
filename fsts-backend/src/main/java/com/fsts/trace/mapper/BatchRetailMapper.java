package com.fsts.trace.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fsts.trace.entity.BatchRetail;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 零售环节明细 Mapper。
 */
@Mapper
public interface BatchRetailMapper extends BaseMapper<BatchRetail> {

    @Select("""
            <script>
            SELECT * FROM batch_retail WHERE batch_id IN
            <foreach collection="ids" item="id" open="(" separator="," close=")">#{id}</foreach>
            </script>
            """)
    List<BatchRetail> selectByBatchIds(@Param("ids") List<Long> ids);
}
