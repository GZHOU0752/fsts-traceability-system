package com.fsts.trace.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fsts.trace.entity.BatchFishing;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 捕捞与养殖环节明细 Mapper。
 */
@Mapper
public interface BatchFishingMapper extends BaseMapper<BatchFishing> {

    @Select("""
            <script>
            SELECT * FROM batch_fishing WHERE batch_id IN
            <foreach collection="ids" item="id" open="(" separator="," close=")">#{id}</foreach>
            </script>
            """)
    List<BatchFishing> selectByBatchIds(@Param("ids") List<Long> ids);
}
