package com.fsts.trace.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 通用流水号表（sys_sequence）—— 为支撑高并发下的编码生成而新增。
 *
 * <p>为什么必须加这张表：企业编码 {@code FSTS-E-20260001}、确认请求单号
 * {@code FSTS-CR-20260921-0001}、溯源码 {@code FSTS-20260915-SH-0001} 都要求
 * "前缀 + 当日/当年 4 位流水"，若用 {@code SELECT MAX(...)+1} 生成，
 * 并发下必然出现重复键冲突并大量回滚。
 *
 * <p>本表通过 {@code INSERT ... ON DUPLICATE KEY UPDATE seq_value = seq_value + 1}
 * 在一条语句内原子取号（行锁粒度单行、无间隙锁），配合业务表唯一索引兜底，
 * 是"高性能 + 强唯一"的折中方案。
 */
@Data
@TableName("sys_sequence")
public class SysSequence implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 序列键，如 enterprise:2026、request:20260921、trace:20260915:SH */
    private String seqKey;

    /** 当前值 */
    private Long seqValue;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
