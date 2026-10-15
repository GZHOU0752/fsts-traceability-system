-- =============================================================================
-- 冷冻海产品溯源系统 - 增量脚本 V4
-- -----------------------------------------------------------------------------
-- 说明：本脚本是对《冷冻海产品溯源系统-数据库建表脚本.sql》的**纯增量**补充，
--       可在原脚本与 V2、V3 执行之后单独执行，不修改任何已有表结构。
-- 日期：2026-10-15
-- =============================================================================

USE `fsts_trace`;

-- -----------------------------------------------------------------------------
-- batch_status_log  产品批号状态流转日志
-- -----------------------------------------------------------------------------
-- 新增原因（可追溯与审计）：
--   product_batch 只保留"当前状态"与 publish_time / off_shelf_time 两个时间戳，
--   无法回答"谁把批号从待确认改成了已确认""谁下架了这个批次"。
--   溯源系统一旦出现争议（例如批次被提前下架导致消费者查不到），
--   没有操作主体与时间的记录就无法复盘，因此把每一次状态流转单独落一行。
--
-- 写入方：BatchStatusLogger（旁路写入，失败只告警不影响业务事务）。
-- 查询方：接口 10.2 批号详情 statusLogs 字段（按时间升序，前端渲染为时间线）。
-- 日志只追加、不提供修改与删除接口。
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `batch_status_log` (
    `id`              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `batch_id`        BIGINT UNSIGNED NOT NULL                COMMENT '产品批号ID',
    `batch_no`        VARCHAR(60)     NOT NULL                COMMENT '产品批号（冗余，便于直接检索）',
    `enterprise_id`   BIGINT UNSIGNED     NULL                COMMENT '所属企业ID',
    `enterprise_name` VARCHAR(100)        NULL                COMMENT '所属企业名称',
    `from_status`     TINYINT             NULL                COMMENT '变更前状态：1 新建，2 待确认，3 已确认，4 已下架；新建时为 NULL',
    `to_status`       TINYINT         NOT NULL                COMMENT '变更后状态',
    `action`          VARCHAR(30)     NOT NULL                COMMENT '动作：新建/发布/发送确认请求/确认进场/拒绝进场/撤回请求/下架/恢复上架',
    `operator_type`   VARCHAR(20)         NULL                COMMENT '操作主体类型：ADMIN / ENTERPRISE / SYSTEM',
    `operator_id`     BIGINT UNSIGNED     NULL                COMMENT '操作主体ID',
    `operator_name`   VARCHAR(100)        NULL                COMMENT '操作主体名称',
    `remark`          VARCHAR(255)        NULL                COMMENT '备注（如下架原因）',
    `create_time`     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '记录时间',
    PRIMARY KEY (`id`),
    KEY `idx_status_log_batch` (`batch_id`, `create_time`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '产品批号状态流转日志';

-- -----------------------------------------------------------------------------
-- 为什么这里**不加外键**（与其它业务表不同）：
--   1. 审计日志的价值恰恰在于"业务数据消失后仍然留存"——新建状态的批号是物理删除的，
--      如果日志跟着级联删除，就查不到"谁建过又删了哪个批号"；
--   2. 建表脚本会 DROP 并重建业务表，子表外键会导致重建顺序依赖，
--      以后每次重置数据库都要先手工删这张表，属于自找的运维成本。
--   因此这里只用索引保证查询效率，由应用层保证 batch_id 的写入正确性。
-- -----------------------------------------------------------------------------

-- =============================================================================
-- 校验：执行完成后应能查到 batch_status_log 表
--   SHOW TABLES LIKE 'batch_status_log';
-- =============================================================================
