package com.fsts.trace.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fsts.trace.entity.BatchStatusLog;
import org.apache.ibatis.annotations.Mapper;

/**
 * 批号状态流转日志 Mapper。
 *
 * <p>只做"追加 + 按批号查询"，日志本身不提供修改/删除入口——
 * 可被篡改的日志等于没有日志。
 */
@Mapper
public interface BatchStatusLogMapper extends BaseMapper<BatchStatusLog> {
}
