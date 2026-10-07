-- =============================================================================
-- 性能优化补充索引
-- 执行方式：mysql -uroot -p fsts_trace < V3__perf_indexes.sql
-- 重复执行会因索引已存在而报错，属预期行为（可用 DROP INDEX 回滚）。
-- =============================================================================

-- 1. 消费者端产品搜索（GET /api/public/products）
--    SQL: WHERE status = 1 AND product_variety LIKE '%kw%' ORDER BY generate_time DESC LIMIT 50
--    产品名是中文子串匹配，前置通配符导致 product_variety 上的索引无法生效，
--    但 (status, generate_time) 组合索引可以让 MySQL 按时间倒序扫描并提前 LIMIT 终止，
--    避免对全部命中行做文件排序。表越大收益越明显。
ALTER TABLE `trace_code`
    ADD INDEX `idx_trace_status_time` (`status`, `generate_time`);

-- 说明：以下候选索引经分析后未采纳，避免"加了索引反而拖慢写入"：
--   * product_batch(upstream_batch_id)：建表脚本已有 idx_batch_upstream，且递归 JOIN 走主键；
--   * product_batch(deleted, id)：递归查询每层都是主键等值定位，该索引不会被选中；
--   * trace_code(product_variety)：查询是 '%kw%' 前置通配，B+Tree 索引无法命中。
-- 若后续搜索改为前缀匹配（LIKE 'kw%'），可再补 product_variety 索引。
