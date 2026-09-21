-- =============================================================================
-- 冷冻海产品溯源系统 - 演示数据脚本
-- -----------------------------------------------------------------------------
-- 用途：支撑《前后端接口文档》附录 G「联调验证清单」的 14 条用例。
-- 依赖：先执行「数据库建表脚本.sql」与「V2__add_sequence_table.sql」。
-- 幂等：脚本开头会清空业务表，可重复执行。
-- 日期：2026-09-22
-- -----------------------------------------------------------------------------
-- 数据规模（与附录 G 第 2 条断言一致）：
--   节点企业 16 家，类型分布 5 / 3 / 5 / 3，覆盖 8 个省级行政区；
--   注册趋势 12 个月连续，数量为 2,2,2,1,1,2,1,1,1,1,1,1。
-- 演示账号（密码均为 123456）：
--   qd_yuanye     捕捞与养殖企业  青岛远洋渔业有限公司
--   yt_haizhen    冷冻加工企业    烟台海珍冷冻食品有限公司
--   sh_yonghai    批发商          上海甬海水产批发有限公司
--   sh_jiaxian    零售商          上海佳鲜生鲜超市有限公司
--   nb_yongjiang  冷冻加工企业    宁波甬江水产加工有限公司
-- =============================================================================

USE `fsts_trace`;
SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- 清空业务数据（保留 sys_admin / sys_region / sys_dict_*）
DELETE FROM `trace_code`;
DELETE FROM `batch_confirm_request`;
DELETE FROM `batch_retail`;
DELETE FROM `batch_wholesale`;
DELETE FROM `batch_processing`;
DELETE FROM `batch_fishing`;
DELETE FROM `product_batch`;
DELETE FROM `node_enterprise`;
DELETE FROM `sys_sequence`;

ALTER TABLE `node_enterprise` AUTO_INCREMENT = 1;
ALTER TABLE `product_batch` AUTO_INCREMENT = 1;
ALTER TABLE `batch_fishing` AUTO_INCREMENT = 1;
ALTER TABLE `batch_processing` AUTO_INCREMENT = 1;
ALTER TABLE `batch_wholesale` AUTO_INCREMENT = 1;
ALTER TABLE `batch_retail` AUTO_INCREMENT = 1;
ALTER TABLE `batch_confirm_request` AUTO_INCREMENT = 1;
ALTER TABLE `trace_code` AUTO_INCREMENT = 1;

-- =============================================================================
-- 一、节点企业（16 家）
-- =============================================================================

-- 类型 1：捕捞与养殖企业（5 家）
-- 资质规则：海洋捕捞填渔业捕捞许可证，水产养殖填水域滩涂养殖证，二者至少其一
INSERT INTO `node_enterprise`
(`id`, `enterprise_code`, `enterprise_name`, `enterprise_type`, `login_name`, `password`, `credit_code`,
 `legal_person`, `contact_person`, `contact_phone`, `province_code`, `province_name`, `city_code`, `city_name`,
 `address`, `register_time`, `fishery_license_no`, `aquaculture_license_no`, `fry_license_no`, `status`)
VALUES
(1, 'FSTS-E-20250001', '青岛远洋渔业有限公司', 1, 'qd_yuanye',
 '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVKIUi', '91370200MA3D1K2X8A',
 '王海生', '刘志强', '13805320001', '370000', '山东省', '370200', '青岛市',
 '青岛市市南区香港中路 88 号远洋大厦 12 层', '2025-10-08 09:12:00', 'LYQZ-2025-3702-0071', NULL, NULL, 1),
(5, 'FSTS-E-20250005', '威海远洋捕捞有限公司', 1, 'wh_yuanyang',
 '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVKIUi', '91371000MA3D3M5Z7C',
 '孙建波', '于海涛', '13806310005', '370000', '山东省', '371000', '威海市',
 '威海市环翠区海滨北路 66 号', '2025-12-03 09:00:00', 'LYQZ-2025-3710-0118', NULL, NULL, 1),
(6, 'FSTS-E-20250006', '舟山海洋捕捞有限公司', 1, 'zs_haiyang',
 '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVKIUi', '91330900MA2A4N6B8D',
 '周海平', '张伟', '13805800006', '330000', '浙江省', '330900', '舟山市',
 '舟山市普陀区沈家门渔港路 21 号', '2025-12-16 11:20:00', 'LYQZ-2025-3309-0042', NULL, NULL, 1),
(7, 'FSTS-E-20260001', '湛江北部湾渔业有限公司', 1, 'zj_beibuwan',
 '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVKIUi', '91440800MA4B5P7C9E',
 '陈国强', '林小军', '13807590007', '440000', '广东省', '440800', '湛江市',
 '湛江市霞山区海滨大道 118 号', '2026-01-14 15:30:00', 'LYQZ-2026-4408-0009', NULL, NULL, 1),
(8, 'FSTS-E-20260002', '大连金海湾水产养殖有限公司', 1, 'dl_jinhaiwan',
 '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVKIUi', '91210200MA0C6Q8D1F',
 '赵立德', '王丽', '13804110008', '210000', '辽宁省', '210200', '大连市',
 '大连市金州区登沙河街道养殖园区 5 号', '2026-02-25 10:05:00', NULL, 'YZQZ-2026-2102-0033', 'MZ-2026-2102-0007', 1);

-- 类型 2：冷冻加工企业（3 家）
INSERT INTO `node_enterprise`
(`id`, `enterprise_code`, `enterprise_name`, `enterprise_type`, `login_name`, `password`, `credit_code`,
 `legal_person`, `contact_person`, `contact_phone`, `province_code`, `province_name`, `city_code`, `city_name`,
 `address`, `register_time`, `food_production_license_no`, `export_filing_no`, `status`)
VALUES
(2, 'FSTS-E-20250002', '烟台海珍冷冻食品有限公司', 2, 'yt_haizhen',
 '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVKIUi', '91370600MA3D2L4Y6B',
 '李振华', '张丽', '13805350002', '370000', '山东省', '370600', '烟台市',
 '烟台市芝罘区幸福南路 156 号冷链产业园 3 号厂房', '2025-10-20 14:30:00', 'SC11337060200136', 'BA2025-3706-0032', 1),
(9, 'FSTS-E-20260003', '宁波甬江水产加工有限公司', 2, 'nb_yongjiang',
 '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVKIUi', '91330200MA2D7R9E2G',
 '吴明辉', '徐芳', '13805740009', '330000', '浙江省', '330200', '宁波市',
 '宁波市北仑区大港工业城水产加工园区 9 号', '2026-03-09 08:50:00', 'SC11333060200089', NULL, 1),
(10, 'FSTS-E-20260004', '珠海海联食品加工有限公司', 2, 'zh_hailian',
 '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVKIUi', '91440400MA4E8S1F3H',
 '黄志强', '梁美玲', '13807560010', '440000', '广东省', '440400', '珠海市',
 '珠海市香洲区唐家湾镇科技创新海岸 12 号', '2026-03-27 16:10:00', 'SC11444040200174', 'BA2026-4404-0011', 1);

-- 类型 3：批发商（5 家）
INSERT INTO `node_enterprise`
(`id`, `enterprise_code`, `enterprise_name`, `enterprise_type`, `login_name`, `password`, `credit_code`,
 `legal_person`, `contact_person`, `contact_phone`, `province_code`, `province_name`, `city_code`, `city_name`,
 `address`, `register_time`, `food_business_license_no`, `road_transport_license_no`,
 `cold_storage_capacity`, `transport_tool_info`, `status`)
VALUES
(3, 'FSTS-E-20250003', '上海甬海水产批发有限公司', 3, 'sh_yonghai',
 '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVKIUi', '91310115MA1H3M8P7C',
 '陈建国', '周敏', '13817800003', '310000', '上海市', '310100', '上海市',
 '上海市浦东新区外高桥保税区洲海路 289 号 2 号冷库', '2025-11-05 10:20:00', 'JY13101150028641',
 '沪交运管许可 310115002863', 3200.00, '冷藏车 6 辆（沪B-LC0231 等）', 1),
(13, 'FSTS-E-20260007', '南通江海水产批发有限公司', 3, 'nt_jianghai',
 '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVKIUi', '91320600MA1H2V4J6L',
 '施建华', '顾小燕', '13805130013', '320000', '江苏省', '320600', '南通市',
 '南通市崇川区滨江冷链物流园 A 区 6 号库', '2026-06-11 10:30:00', 'JY13206020031845',
 '苏交运管许可 320600129873', 1850.00, '冷藏车 3 辆（苏F-LC0088 等）', 1),
(14, 'FSTS-E-20260008', '海口南海水产批发有限公司', 3, 'hk_nanhai',
 '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVKIUi', '91460100MA5I3W5K7M',
 '符志明', '王琼', '13807680014', '460000', '海南省', '460100', '海口市',
 '海口市秀英区港澳大道冷链物流中心 3 号库', '2026-07-08 09:15:00', 'JY14601010022736',
 '琼交运管许可 460100055412', 960.00, '冷藏车 2 辆（琼A-LC0166 等）', 1),
(15, 'FSTS-E-20260009', '福州闽江水产批发有限公司', 3, 'fz_minjiang',
 '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVKIUi', '91350100MA3J4X6L8N',
 '郑立诚', '林芳', '13805910015', '350000', '福建省', '350100', '福州市',
 '福州市马尾区闽江口冷链物流园 B 栋', '2026-08-19 15:45:00', 'JY13501020019473',
 '闽交运管许可 350100318842', 1420.00, '冷藏车 4 辆（闽A-LC0325 等）', 1),
(16, 'FSTS-E-20260010', '威海石岛水产批发有限公司', 3, 'wh_shidao',
 '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVKIUi', '91371000MA3K5Y7M9P',
 '毕海涛', '鞠晓丽', '13806310016', '370000', '山东省', '371000', '威海市',
 '威海市荣成市石岛管理区渔港路 77 号', '2026-09-02 09:30:00', 'JY13710820026154',
 '鲁交运管许可 371000447291', 2600.00, '冷藏车 5 辆（鲁K-LC0517 等）', 1);

-- 类型 4：零售商（3 家）
INSERT INTO `node_enterprise`
(`id`, `enterprise_code`, `enterprise_name`, `enterprise_type`, `login_name`, `password`, `credit_code`,
 `legal_person`, `contact_person`, `contact_phone`, `province_code`, `province_name`, `city_code`, `city_name`,
 `address`, `register_time`, `food_business_license_no`, `display_equipment_info`, `status`)
VALUES
(4, 'FSTS-E-20250004', '上海佳鲜生鲜超市有限公司', 4, 'sh_jiaxian',
 '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVKIUi', '91310115MA1H4N9Q8D',
 '赵晓燕', '孙涛', '13817800004', '310000', '上海市', '310100', '上海市',
 '上海市浦东新区世纪大道 100 号佳鲜生鲜超市陆家嘴店', '2025-11-19 16:48:00', 'JY13101150031972',
 '冷冻岛柜 8 台（LG-01 至 LG-08，单体温度 -20 ℃）', 1),
(11, 'FSTS-E-20260005', '深圳海鲜优选超市有限公司', 4, 'sz_haixian',
 '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVKIUi', '91440300MA5F9T2G4J',
 '何俊杰', '刘婷', '13807550011', '440000', '广东省', '440300', '深圳市',
 '深圳市南山区海德三道 158 号海鲜优选超市', '2026-04-15 09:40:00', 'JY14403050041826',
 '冷冻岛柜 10 台（SZ-01 至 SZ-10）', 1),
(12, 'FSTS-E-20260006', '福州鲜汇超市有限公司', 4, 'fz_xianhui',
 '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVKIUi', '91350100MA3G1U3H5K',
 '高志远', '陈静', '13805910012', '350000', '福建省', '350100', '福州市',
 '福州市鼓楼区五四路 268 号鲜汇超市', '2026-05-22 14:00:00', 'JY13501020026317',
 '冷冻陈列柜 6 台（FZ-01 至 FZ-06）', 1);

-- =============================================================================
-- 二、产品批号主表（一条完整的四环节溯源链 + 一条待确认 + 一条新建 + 一条已下架）
-- =============================================================================
-- id=1  YY20260901001  青岛远洋（捕捞与养殖）  已发布  <- 链条第 1 环
-- id=2  JG20260905001  烟台海珍（冷冻加工）    已发布  <- 链条第 2 环
-- id=3  PF20260910001  上海甬海（批发）        已发布  <- 链条第 3 环
-- id=4  LS20260915001  上海佳鲜（零售）        已确认  <- 链条第 4 环，已生成溯源标识码
-- id=7  JG20260920001  烟台海珍（冷冻加工）    新建    <- 支撑接口 10.4 更新用例
-- id=8  PF20260919001  上海甬海（批发）        待确认  <- 支撑接口 11.1/11.3 与重复发送用例
-- id=10 YY20260908001  青岛远洋（捕捞与养殖）  已下架  <- 支撑接口 10.7，且不应出现在列表中
-- 说明：id 刻意留出 5 / 6 / 9 空位，使关键样例的 ID 与接口文档示例保持一致。
-- =============================================================================
INSERT INTO `product_batch`
(`id`, `batch_no`, `enterprise_id`, `enterprise_name`, `enterprise_type`, `product_variety`, `source_type`,
 `batch_status`, `handover_temp`, `cold_chain_ok`, `publish_time`, `off_shelf_time`, `remark`,
 `create_time`, `update_time`, `deleted`)
VALUES
(1, 'YY20260901001', 1, '青岛远洋渔业有限公司', 1, '带鱼', 2,
 3, -19.50, 1, '2026-09-01 08:30:00', NULL, '2026 年第 17 航次渔获',
 '2026-09-01 08:20:00', '2026-09-01 08:30:00', 0),
(2, 'JG20260905001', 2, '烟台海珍冷冻食品有限公司', 2, '带鱼', 2,
 3, -19.00, 1, '2026-09-05 16:20:00', NULL, '速冻后分切包装',
 '2026-09-05 16:05:00', '2026-09-05 16:20:00', 0),
(3, 'PF20260910001', 3, '上海甬海水产批发有限公司', 3, '带鱼', 2,
 3, -18.50, 1, '2026-09-10 09:15:00', NULL, '外高桥冷库中转',
 '2026-09-10 09:00:00', '2026-09-10 09:15:00', 0),
(4, 'LS20260915001', 4, '上海佳鲜生鲜超市有限公司', 4, '带鱼', 2,
 3, -18.20, 1, '2026-09-15 07:40:00', NULL, '陆家嘴店上架销售，柜台陈列温度 -18.2 ℃',
 '2026-09-15 07:20:00', '2026-09-15 07:40:00', 0),
(7, 'JG20260920001', 2, '烟台海珍冷冻食品有限公司', 2, '带鱼', 2,
 1, -19.20, 1, NULL, NULL, '待发送确认请求',
 '2026-09-20 10:05:00', '2026-09-20 10:05:00', 0),
(8, 'PF20260919001', 3, '上海甬海水产批发有限公司', 3, '带鱼', 2,
 2, -18.60, 1, NULL, NULL, '已向上游发送确认请求，等待烟台海珍确认',
 '2026-09-19 14:00:00', '2026-09-19 14:12:00', 0),
(10, 'YY20260908001', 1, '青岛远洋渔业有限公司', 1, '带鱼', 2,
 4, -19.80, 1, '2026-09-08 09:00:00', '2026-09-18 11:20:00', '航次记录更正，原批号停用',
 '2026-09-08 08:50:00', '2026-09-18 11:20:00', 0);

-- 上游带出字段（类型 2/3/4 的批号从上游批号继承品种与来源，并记录上游企业所在省市）
UPDATE `product_batch` SET
    `upstream_enterprise_id` = 1, `upstream_enterprise_name` = '青岛远洋渔业有限公司',
    `upstream_province_code` = '370000', `upstream_province_name` = '山东省',
    `upstream_city_code` = '370200', `upstream_city_name` = '青岛市',
    `upstream_batch_id` = 1, `upstream_batch_no` = 'YY20260901001'
 WHERE `id` IN (2, 7);

UPDATE `product_batch` SET
    `upstream_enterprise_id` = 2, `upstream_enterprise_name` = '烟台海珍冷冻食品有限公司',
    `upstream_province_code` = '370000', `upstream_province_name` = '山东省',
    `upstream_city_code` = '370600', `upstream_city_name` = '烟台市',
    `upstream_batch_id` = 2, `upstream_batch_no` = 'JG20260905001'
 WHERE `id` = 3;

UPDATE `product_batch` SET
    `upstream_enterprise_id` = 2, `upstream_enterprise_name` = '烟台海珍冷冻食品有限公司',
    `upstream_province_code` = '370000', `upstream_province_name` = '山东省',
    `upstream_city_code` = '370600', `upstream_city_name` = '烟台市',
    `upstream_batch_id` = 2, `upstream_batch_no` = 'JG20260905001'
 WHERE `id` = 8;

UPDATE `product_batch` SET
    `upstream_enterprise_id` = 3, `upstream_enterprise_name` = '上海甬海水产批发有限公司',
    `upstream_province_code` = '310000', `upstream_province_name` = '上海市',
    `upstream_city_code` = '310100', `upstream_city_name` = '上海市',
    `upstream_batch_id` = 3, `upstream_batch_no` = 'PF20260910001'
 WHERE `id` = 4;

-- =============================================================================
-- 三、环节明细
-- =============================================================================

-- 捕捞与养殖环节
INSERT INTO `batch_fishing`
(`batch_id`, `catch_breed_date`, `certificate_type`, `certificate_no`, `departure_temp`,
 `drug_report_no`, `fishing_log_no`)
VALUES
(1, '2026-08-28', 2, 'SHZ-2026-0828-0157', -19.50, 'YWJC-2026-0912', 'YL-2026-0828-017'),
(10, '2026-09-05', 2, 'SHZ-2026-0905-0188', -19.80, NULL, 'YL-2026-0905-021');

-- 冷冻加工环节
INSERT INTO `batch_processing`
(`batch_id`, `process_form`, `inspection_no`, `quick_freeze_temp`, `factory_temp`, `production_batch_no`)
VALUES
(2, '鱼段', 'CYJ-2026-0905-0233', -36.20, -19.00, 'SC-2026-0905-014'),
(7, '鱼片', 'CYJ-2026-0920-0301', -36.50, -19.20, 'SC-2026-0920-021');

-- 批发与冷链储运环节
INSERT INTO `batch_wholesale`
(`batch_id`, `wholesale_date`, `inbound_temp`, `cold_storage_temp`, `outbound_temp`,
 `transport_tool_no`, `cold_storage_no`)
VALUES
(3, '2026-09-10', -18.60, -18.80, -18.50, '沪B-LC0231', 'SH-LL-02'),
(8, '2026-09-19', -18.70, -18.90, -18.60, '沪B-LC0231', 'SH-LL-02');

-- 零售环节
INSERT INTO `batch_retail`
(`batch_id`, `shelf_date`, `display_temp`, `sale_store`, `display_equip_no`)
VALUES
(4, '2026-09-15', -18.20, '佳鲜生鲜超市 陆家嘴店', 'LG-03');

-- =============================================================================
-- 四、进场确认请求
-- =============================================================================
-- id=1  已确认：上海佳鲜 -> 上海甬海，支撑接口 10.2 详情中的 confirmRequest 片段
-- id=2  待确认：上海甬海 -> 烟台海珍，支撑接口 11.1 / 11.2 / 11.3，
--       同时支撑附录 G 第 8 条「重复发送确认请求应返回 409」
-- =============================================================================
INSERT INTO `batch_confirm_request`
(`id`, `request_no`, `batch_id`, `batch_no`, `upstream_batch_id`, `upstream_batch_no`,
 `from_enterprise_id`, `from_enterprise_name`, `to_enterprise_id`, `to_enterprise_name`,
 `handover_temp`, `request_status`, `request_time`, `handle_time`, `handle_remark`)
VALUES
(1, 'FSTS-CR-20260914-0001', 4, 'LS20260915001', 3, 'PF20260910001',
 4, '上海佳鲜生鲜超市有限公司', 3, '上海甬海水产批发有限公司',
 -18.20, 2, '2026-09-14 18:20:00', '2026-09-15 07:35:00', '实收数量与温度记录相符，同意确认'),
(2, 'FSTS-CR-20260919-0001', 8, 'PF20260919001', 2, 'JG20260905001',
 3, '上海甬海水产批发有限公司', 2, '烟台海珍冷冻食品有限公司',
 -18.60, 1, '2026-09-19 14:12:00', NULL, NULL);

-- =============================================================================
-- 五、溯源标识码
-- =============================================================================
-- 由零售商批号 LS20260915001（id=4）在确认进场时自动生成，
-- 消费者端输入该标识码可查询到完整的四环节溯源链。
-- =============================================================================
INSERT INTO `trace_code`
(`id`, `trace_code`, `batch_id`, `batch_no`, `retailer_id`, `retailer_name`, `product_variety`,
 `sale_store`, `qr_content`, `status`, `query_count`, `last_query_time`, `generate_time`)
VALUES
(1, 'FSTS-20260915-SH-0001', 4, 'LS20260915001', 4, '上海佳鲜生鲜超市有限公司', '带鱼',
 '佳鲜生鲜超市 陆家嘴店', 'https://fsts.example.com/trace/FSTS-20260915-SH-0001',
 1, 12, '2026-09-20 19:22:00', '2026-09-15 07:36:00');

-- =============================================================================
-- 六、流水号基线
-- =============================================================================
-- 必须与上面手工插入的编码对齐，否则新增数据时会生成重复编码。
--   enterprise:2025     2025 年已注册 6 家   -> 下一次为 FSTS-E-20250007
--   enterprise:2026     2026 年已注册 10 家  -> 下一次为 FSTS-E-20260011
--   request:20260914    当日已用 1 个单号
--   request:20260919    当日已用 1 个单号
--   trace:20260915:SH   当日上海已用 1 个溯源码
-- =============================================================================
INSERT INTO `sys_sequence` (`seq_key`, `seq_value`)
VALUES
('enterprise:2025', 6),
('enterprise:2026', 10),
('request:20260914', 1),
('request:20260919', 1),
('trace:20260915:SH', 1);

SET FOREIGN_KEY_CHECKS = 1;

-- =============================================================================
-- 七、导入校验（执行后请核对输出）
-- =============================================================================
SELECT '节点企业总数' AS item, COUNT(*) AS value FROM `node_enterprise`
UNION ALL
SELECT CONCAT('类型 ', enterprise_type), COUNT(*) FROM `node_enterprise` GROUP BY enterprise_type
UNION ALL
SELECT '覆盖省份数', COUNT(DISTINCT province_code) FROM `node_enterprise`
UNION ALL
SELECT '产品批号数', COUNT(*) FROM `product_batch`
UNION ALL
SELECT '溯源标识码数', COUNT(*) FROM `trace_code`
UNION ALL
SELECT '待确认请求数', COUNT(*) FROM `batch_confirm_request` WHERE request_status = 1;

-- 期望结果：
--   节点企业总数 16 / 类型 1:5、2:3、3:5、4:3 / 覆盖省份数 8
--   产品批号数 7 / 溯源标识码数 1 / 待确认请求数 1
