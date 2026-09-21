package com.fsts.trace.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 流水号 Mapper：原子取号，避免并发下的重复编号。
 *
 * <p><b>为什么不用 {@code LAST_INSERT_ID(expr)} 取号（重要）：</b>
 * 直觉上可以写成 {@code INSERT ... VALUES (?, LAST_INSERT_ID(1)) ON DUPLICATE KEY
 * UPDATE seq_value = LAST_INSERT_ID(seq_value + 1)}，再用
 * {@code SELECT LAST_INSERT_ID()} 读回，两条语句即可完成取号。
 * 但这里有一个隐蔽陷阱：<b>当 INSERT 真正插入新行时，表的 AUTO_INCREMENT 会覆盖
 * 语句中显式设置的 LAST_INSERT_ID</b>，于是首次取号返回的是该行的自增主键
 * （例如 6），而行里存的却是 1。后续取号从 2 开始递增，
 * 序列将变成 6, 2, 3, 4, 5, 6 —— 第 6 次取号必然与第 1 次重复。
 *
 * <p>因此改为"先 UPSERT、再读回实际值"：两条语句都直接操作 {@code seq_value} 列，
 * 不依赖任何会话级隐式状态，结果完全确定。
 * 两条语句必须处于同一事务（见 {@code SequenceService}），
 * 这样读取到的一定是本事务刚写入的值，且行锁在事务提交后立即释放。
 */
@Mapper
public interface SysSequenceMapper {

    @Insert("""
            INSERT INTO sys_sequence (seq_key, seq_value, create_time, update_time)
            VALUES (#{seqKey}, 1, NOW(), NOW())
            ON DUPLICATE KEY UPDATE
                seq_value = seq_value + 1,
                update_time = NOW()
            """)
    int nextValue(@Param("seqKey") String seqKey);

    @Select("SELECT seq_value FROM sys_sequence WHERE seq_key = #{seqKey}")
    long currentValue(@Param("seqKey") String seqKey);
}
