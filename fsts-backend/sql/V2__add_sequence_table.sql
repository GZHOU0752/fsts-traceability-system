-- =============================================================================
-- 冷冻海产品溯源系统 - 增量脚本 V2
-- -----------------------------------------------------------------------------
-- 说明：本脚本是对《冷冻海产品溯源系统-数据库建表脚本.sql》的**纯增量**补充，
--       不修改原脚本的任何表结构，可在原脚本执行之后单独执行。
-- 日期：2026-09-21
-- =============================================================================

USE `fsts_trace`;

-- -----------------------------------------------------------------------------
-- sys_sequence  通用流水号表
-- -----------------------------------------------------------------------------
-- 新增原因（高并发下的编码生成）：
--   系统需要生成三类带"序号"的业务编码：
--     企业编码      FSTS-E-{年份}{4 位流水}         例 FSTS-E-20260017
--     确认请求单号  FSTS-CR-{yyyyMMdd}-{4 位流水}    例 FSTS-CR-20260921-0001
--     溯源标识码    FSTS-{yyyyMMdd}-{省份}-{4 位流水} 例 FSTS-20260915-SH-0001
--   若用 SELECT MAX(...)+1 或 COUNT(*)+1 生成：
--     - 并发下多个事务读到同一个 max，必然撞唯一索引；
--     - 冲突后大量事务回滚重试，形成"活锁"，吞吐急剧下降；
--     - 即使加 SELECT ... FOR UPDATE，也会因为锁住范围而产生间隙锁与死锁。
--   本表采用 "前缀 + 当日/当年 4 位流水" 的独立计数行，
--   用 INSERT ... ON DUPLICATE KEY UPDATE seq_value = LAST_INSERT_ID(seq_value + 1)
--   在单条语句内完成"原子取号"，锁粒度精确到一行，无间隙锁、无死锁风险。
--   业务表上的唯一索引（uk_enterprise_code / uk_request_no / uk_trace_code）
--   作为最后一道兜底，确保任何异常情况下都不会产生重复编码。
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `sys_sequence` (
    `id`          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `seq_key`     VARCHAR(80)     NOT NULL                COMMENT '序列键，如 enterprise:2026、trace:20260915:SH',
    `seq_value`   BIGINT UNSIGNED NOT NULL DEFAULT 0      COMMENT '当前序号值',
    `create_time` DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_sequence_key` (`seq_key`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '通用流水号表（企业编码 / 确认请求单号 / 溯源标识码取号）';

-- -----------------------------------------------------------------------------
-- 性能补充索引（可选，按实际慢查询情况启用）
-- -----------------------------------------------------------------------------
-- 说明：product_batch 已有 idx_batch_enterprise_status(enterprise_id, batch_status, deleted)，
--       能够覆盖"本企业 + 指定状态"的列表查询。
--       下列索引用于"消费者端按标识码反查后，再按上游批号批量取明细"的场景，
--       以及管理端大屏按注册时间做范围统计的场景，属于可选优化，可按需执行。
-- -----------------------------------------------------------------------------

-- 溯源链递归查询：由上游批号反查下游批号（当前只有正向 idx_batch_upstream）
-- 若溯源链查询出现回表过多，可考虑补充；MySQL 8 对递归 CTE 的索引选择已较优，默认不建。

-- 确认请求处理时间排序（历史记录查询场景）
-- ALTER TABLE `batch_confirm_request` ADD INDEX `idx_request_handle_time` (`handle_time`);

-- 溯源码查询时间排序（运营分析场景）
-- ALTER TABLE `trace_code` ADD INDEX `idx_trace_last_query_time` (`last_query_time`);

-- =============================================================================
-- 校验：执行完成后应能查到 sys_sequence 表
--   SHOW TABLES LIKE 'sys_sequence';
-- =============================================================================
