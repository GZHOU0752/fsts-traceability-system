-- =============================================================================
-- 冷冻海产品溯源系统（FSTS，Frozen Seafood Traceability System）
-- 数据库建表脚本
-- -----------------------------------------------------------------------------
-- 文件编号 : XG1232-FSTS-DB-01
-- 数据 库  : MySQL 8.0（InnoDB / utf8mb4 / utf8mb4_0900_ai_ci）
-- 版    本 : 1.0.0-0.0.0
-- 日    期 : 2026-09-21
-- 依    据 : 《01 冷冻海产品溯源系统-需求陈述书》
--            《04 冷冻海产品溯源系统-需求规格说明书》
--            《信管1232-冷冻海产品溯源系统-项目任务书》
-- -----------------------------------------------------------------------------
-- 设计说明（与需求规格说明书的对应关系）：
--   1. 系统分三端：系统管理端、流通节点端、消费者端。
--      - 系统管理端账号        -> sys_admin
--      - 流通节点端账号        -> node_enterprise（四类节点企业共用一张表，用
--                                enterprise_type 区分，差异化资质字段允许为空）
--      - 消费者端无账号        -> 匿名访问 /api/public/**
--   2. 产品批号（核心业务对象）采用「主表 + 环节明细表」结构：
--      - product_batch      : 四个环节共有的数据项（批号、品种、上游信息、状态、交接温度）
--      - batch_fishing      : 捕捞与养殖环节独有数据项
--      - batch_processing   : 冷冻加工环节独有数据项
--      - batch_wholesale    : 批发与冷链储运环节独有数据项（入库/冷库/出库温度）
--      - batch_retail       : 零售环节独有数据项
--      这样既保证"同一批号在全链条上字段一致"，又避免单表出现大量空字段。
--   3. 产品批号状态机（product_batch.batch_status）：
--        1 新建/待发布 --发布--> 3 已确认/已发布 --下架--> 4 已下架
--        1 新建 --勾选"向上游发送确认请求"--发送请求--> 2 待确认 --上游确认--> 3 已确认
--        1 新建 --删除--> 物理删除（仅"新建"状态允许删除）
--      捕捞与养殖企业处于链条源头，没有"待确认"状态。
--   4. 进场确认请求单独建表（batch_confirm_request），用于记录确认过程与结果，
--      与 product_batch.batch_status 保持同步：请求生成时批号置 2，确认后置 3。
--   5. 溯源标识码（trace_code）在零售商产品批号状态变为"已确认"时由系统自动生成，
--      在同一批次内全局唯一；批号下架后标识码同步失效。
--   6. 逻辑删除：除明细表外的主表均含 deleted 字段（0 未删除 / 1 已删除）。
--      逻辑删除记录仍占用唯一索引，因此"删除后重新注册"需复用原记录（
--      把 deleted 置回 0）而不是新增记录，避免触发唯一约束冲突。
-- =============================================================================

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- -----------------------------------------------------------------------------
-- 0. 创建数据库
--    Spring Boot 连接串示例：
--    jdbc:mysql://localhost:3306/fsts_trace?useUnicode=true&characterEncoding=utf8
--        &serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true
-- -----------------------------------------------------------------------------
CREATE DATABASE IF NOT EXISTS `fsts_trace`
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_0900_ai_ci;

USE `fsts_trace`;

-- 按依赖倒序删除旧表，保证脚本可重复执行
DROP TABLE IF EXISTS `trace_code`;
DROP TABLE IF EXISTS `batch_confirm_request`;
DROP TABLE IF EXISTS `batch_retail`;
DROP TABLE IF EXISTS `batch_wholesale`;
DROP TABLE IF EXISTS `batch_processing`;
DROP TABLE IF EXISTS `batch_fishing`;
DROP TABLE IF EXISTS `product_batch`;
DROP TABLE IF EXISTS `node_enterprise`;
DROP TABLE IF EXISTS `sys_dict_item`;
DROP TABLE IF EXISTS `sys_dict_type`;
DROP TABLE IF EXISTS `sys_region`;
DROP TABLE IF EXISTS `sys_admin`;

DROP VIEW IF EXISTS `v_enterprise_province_stat`;
DROP VIEW IF EXISTS `v_enterprise_type_stat`;
DROP VIEW IF EXISTS `v_batch_trace_chain`;


-- =============================================================================
-- 一、系统管理端
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 1. sys_admin  系统管理员表
-- -----------------------------------------------------------------------------
DROP TABLE IF EXISTS `sys_admin`;
CREATE TABLE `sys_admin` (
    `id`              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `login_name`      VARCHAR(50)     NOT NULL                COMMENT '登录账号',
    `password`        VARCHAR(100)    NOT NULL                COMMENT '登录密码（BCrypt 密文）',
    `admin_name`      VARCHAR(50)     NOT NULL                COMMENT '管理员姓名',
    `phone`           VARCHAR(20)         NULL                COMMENT '联系电话',
    `email`           VARCHAR(100)        NULL                COMMENT '电子邮箱',
    `status`          TINYINT         NOT NULL DEFAULT 1      COMMENT '账号状态：1 启用，0 停用',
    `last_login_time` DATETIME            NULL                COMMENT '最近登录时间',
    `create_time`     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`         TINYINT         NOT NULL DEFAULT 0      COMMENT '逻辑删除：0 未删除，1 已删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_admin_login_name` (`login_name`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '系统管理员表';


-- =============================================================================
-- 二、基础数据（省市区划、数据字典）
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 2. sys_region  行政区划表（省 / 市）
--    用途：节点企业注册信息管理页面的"省下拉列表""市下拉列表"；
--          可视化大屏"以省分组统计企业注册数量"。
-- -----------------------------------------------------------------------------
DROP TABLE IF EXISTS `sys_region`;
CREATE TABLE `sys_region` (
    `id`           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `region_code`  CHAR(6)         NOT NULL                COMMENT '行政区划代码（GB/T 2260）',
    `region_name`  VARCHAR(50)     NOT NULL                COMMENT '行政区划名称',
    `region_level` TINYINT         NOT NULL                COMMENT '层级：1 省/直辖市/自治区，2 市',
    `parent_code`  CHAR(6)         NOT NULL DEFAULT '000000' COMMENT '上级行政区划代码，省级固定为 000000',
    `short_name`   VARCHAR(20)         NULL                COMMENT '简称（如"山东""青岛"）',
    `sort_no`      INT             NOT NULL DEFAULT 0      COMMENT '显示排序号',
    `status`       TINYINT         NOT NULL DEFAULT 1      COMMENT '状态：1 启用，0 停用',
    `create_time`  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_region_code` (`region_code`),
    KEY `idx_region_parent` (`parent_code`, `region_level`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '行政区划表（省/市）';


-- -----------------------------------------------------------------------------
-- 3. sys_dict_type  数据字典类型表
-- -----------------------------------------------------------------------------
DROP TABLE IF EXISTS `sys_dict_type`;
CREATE TABLE `sys_dict_type` (
    `id`          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `type_code`   VARCHAR(50)     NOT NULL                COMMENT '字典类型编码',
    `type_name`   VARCHAR(50)     NOT NULL                COMMENT '字典类型名称',
    `remark`      VARCHAR(200)        NULL                COMMENT '说明',
    `status`      TINYINT         NOT NULL DEFAULT 1      COMMENT '状态：1 启用，0 停用',
    `create_time` DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_dict_type_code` (`type_code`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '数据字典类型表';


-- -----------------------------------------------------------------------------
-- 4. sys_dict_item  数据字典项表
-- -----------------------------------------------------------------------------
DROP TABLE IF EXISTS `sys_dict_item`;
CREATE TABLE `sys_dict_item` (
    `id`          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `type_code`   VARCHAR(50)     NOT NULL                COMMENT '字典类型编码',
    `item_code`   VARCHAR(50)     NOT NULL                COMMENT '字典项编码',
    `item_value`  VARCHAR(100)    NOT NULL                COMMENT '字典项名称',
    `sort_no`     INT             NOT NULL DEFAULT 0      COMMENT '显示排序号',
    `status`      TINYINT         NOT NULL DEFAULT 1      COMMENT '状态：1 启用，0 停用',
    `remark`      VARCHAR(200)        NULL                COMMENT '说明',
    `create_time` DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_dict_item` (`type_code`, `item_code`),
    KEY `idx_dict_item_type` (`type_code`, `status`, `sort_no`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '数据字典项表';


-- =============================================================================
-- 三、节点企业（流通节点端账号 + 系统管理端管理的注册信息）
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 5. node_enterprise  节点企业注册信息表
--    四类节点企业共用本表，用 enterprise_type 区分；不同企业类型的资质证件
--    字段允许为空，由业务层按类型做必填校验（见需求规格说明书 4.1 事件描述:
--    "根据选择的企业类型切换资质证件填写项"）。
-- -----------------------------------------------------------------------------
DROP TABLE IF EXISTS `node_enterprise`;
CREATE TABLE `node_enterprise` (
    `id`                          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `enterprise_code`             VARCHAR(30)     NOT NULL                COMMENT '企业编码（系统生成，如 FSTS-E-20260001）',
    `enterprise_name`             VARCHAR(100)    NOT NULL                COMMENT '企业名称',
    `enterprise_type`             TINYINT         NOT NULL                COMMENT '企业类型：1 捕捞与养殖企业，2 冷冻加工企业，3 批发商，4 零售商',
    `login_name`                  VARCHAR(50)     NOT NULL                COMMENT '登录账号',
    `password`                    VARCHAR(100)    NOT NULL                COMMENT '登录密码（BCrypt 密文）',

    -- 企业基本信息
    `credit_code`                 VARCHAR(50)     NOT NULL                COMMENT '统一社会信用代码（营业执照编号）',
    `legal_person`                VARCHAR(50)         NULL                COMMENT '法定代表人',
    `contact_person`              VARCHAR(50)         NULL                COMMENT '联系人',
    `contact_phone`               VARCHAR(20)         NULL                COMMENT '联系电话',
    `province_code`               CHAR(6)         NOT NULL                COMMENT '所在省行政区划代码',
    `province_name`               VARCHAR(50)     NOT NULL                COMMENT '所在省名称',
    `city_code`                   CHAR(6)         NOT NULL                COMMENT '所在市行政区划代码',
    `city_name`                   VARCHAR(50)     NOT NULL                COMMENT '所在市名称',
    `address`                     VARCHAR(200)        NULL                COMMENT '详细地址',
    `register_time`               DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '注册时间（统计注册趋势用）',

    -- 捕捞与养殖企业（enterprise_type = 1）资质证件
    `fishery_license_no`          VARCHAR(60)         NULL                COMMENT '渔业捕捞许可证编号（海洋捕捞）',
    `aquaculture_license_no`      VARCHAR(60)         NULL                COMMENT '水域滩涂养殖证编号（水产养殖）',
    `fry_license_no`              VARCHAR(60)         NULL                COMMENT '水产苗种生产许可证编号（苗种繁育）',

    -- 冷冻加工企业（enterprise_type = 2）资质证件
    `food_production_license_no`  VARCHAR(60)         NULL                COMMENT '食品生产许可证编号',
    `export_filing_no`            VARCHAR(60)         NULL                COMMENT '出口食品生产企业备案编号（出口产品）',

    -- 批发商（enterprise_type = 3）、零售商（enterprise_type = 4）资质证件
    `food_business_license_no`    VARCHAR(60)         NULL                COMMENT '食品经营许可证编号',
    `road_transport_license_no`   VARCHAR(60)         NULL                COMMENT '道路运输经营许可证编号（自营冷藏运输）',

    -- 批发商经营能力信息（enterprise_type = 3）
    `cold_storage_capacity`       DECIMAL(12, 2)      NULL                COMMENT '冷库容量（吨）',
    `transport_tool_info`         VARCHAR(200)        NULL                COMMENT '冷藏运输工具信息（车辆/船舶数量与编号）',

    -- 零售商设备信息（enterprise_type = 4）
    `display_equipment_info`      VARCHAR(200)        NULL                COMMENT '冷冻陈列设备信息（陈列柜数量与编号）',

    `status`                      TINYINT         NOT NULL DEFAULT 1      COMMENT '企业状态：1 正常，0 停用（停用后不能参与溯源链）',
    `last_login_time`             DATETIME            NULL                COMMENT '最近登录时间',
    `create_time`                 DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`                 DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`                     TINYINT         NOT NULL DEFAULT 0      COMMENT '逻辑删除：0 未删除，1 已删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_enterprise_code` (`enterprise_code`),
    UNIQUE KEY `uk_enterprise_login_name` (`login_name`),
    UNIQUE KEY `uk_enterprise_credit_code` (`credit_code`),
    KEY `idx_enterprise_type` (`enterprise_type`, `deleted`),
    KEY `idx_enterprise_region` (`province_code`, `city_code`),
    KEY `idx_enterprise_register_time` (`register_time`),
    KEY `idx_enterprise_name` (`enterprise_name`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '节点企业注册信息表';


-- =============================================================================
-- 四、产品批号（核心业务对象）
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 6. product_batch  产品批号主表
--    四个流通环节共有的数据项；批号由企业原有管理系统生成后手工录入，
--    系统在同一企业内校验唯一性。
-- -----------------------------------------------------------------------------
DROP TABLE IF EXISTS `product_batch`;
CREATE TABLE `product_batch` (
    `id`                     BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `batch_no`               VARCHAR(60)     NOT NULL                COMMENT '产品批号（企业原有管理系统生成，手工录入）',
    `enterprise_id`          BIGINT UNSIGNED NOT NULL                COMMENT '所属节点企业ID（出场企业）',
    `enterprise_name`        VARCHAR(100)    NOT NULL                COMMENT '所属节点企业名称（冗余，便于溯源展示）',
    `enterprise_type`        TINYINT         NOT NULL                COMMENT '所属环节：1 捕捞与养殖，2 冷冻加工，3 批发，4 零售',

    -- 上游带出的数据项（源头环节为空）
    `upstream_enterprise_id` BIGINT UNSIGNED     NULL                COMMENT '上游企业ID',
    `upstream_enterprise_name` VARCHAR(100)      NULL                COMMENT '上游企业名称',
    `upstream_province_code` CHAR(6)             NULL                COMMENT '上游企业所在省代码',
    `upstream_province_name` VARCHAR(50)         NULL                COMMENT '上游企业所在省名称',
    `upstream_city_code`     CHAR(6)             NULL                COMMENT '上游企业所在市代码',
    `upstream_city_name`     VARCHAR(50)         NULL                COMMENT '上游企业所在市名称',
    `upstream_batch_id`      BIGINT UNSIGNED     NULL                COMMENT '上游产品批号ID',
    `upstream_batch_no`      VARCHAR(60)         NULL                COMMENT '上游产品批号',

    `product_variety`        VARCHAR(50)     NOT NULL                COMMENT '产品品种（由上游带出后不可修改）',
    `source_type`            TINYINT             NULL                COMMENT '来源类型：1 养殖，2 捕捞（仅捕捞与养殖环节使用，其余环节继承上游值）',
    `batch_status`           TINYINT         NOT NULL DEFAULT 1      COMMENT '批号状态：1 新建/待发布，2 待确认，3 已确认/已发布，4 已下架',
    `handover_temp`          DECIMAL(5, 2)       NULL                COMMENT '本环节交接温度（℃）：捕捞起运温度、加工出厂温度、批发出库温度、零售陈列柜温度',
    `cold_chain_ok`          TINYINT         NOT NULL DEFAULT 1      COMMENT '本环节冷链是否合格：1 合格，0 不合格',

    `publish_time`           DATETIME            NULL                COMMENT '发布/确认时间（对外可用时间）',
    `off_shelf_time`         DATETIME            NULL                COMMENT '下架时间',
    `remark`                 VARCHAR(255)        NULL                COMMENT '备注',
    `create_time`            DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`            DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`                TINYINT         NOT NULL DEFAULT 0      COMMENT '逻辑删除：0 未删除，1 已删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_batch_enterprise_no` (`enterprise_id`, `batch_no`) COMMENT '同一企业内产品批号唯一',
    KEY `idx_batch_upstream` (`upstream_batch_id`),
    KEY `idx_batch_enterprise_status` (`enterprise_id`, `batch_status`, `deleted`),
    KEY `idx_batch_status` (`batch_status`),
    KEY `idx_batch_variety` (`product_variety`),
    KEY `idx_batch_create_time` (`create_time`),
    CONSTRAINT `fk_batch_enterprise` FOREIGN KEY (`enterprise_id`) REFERENCES `node_enterprise` (`id`),
    CONSTRAINT `fk_batch_upstream_enterprise` FOREIGN KEY (`upstream_enterprise_id`) REFERENCES `node_enterprise` (`id`),
    CONSTRAINT `fk_batch_upstream_batch` FOREIGN KEY (`upstream_batch_id`) REFERENCES `product_batch` (`id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '产品批号主表';


-- -----------------------------------------------------------------------------
-- 7. batch_fishing  捕捞与养殖环节产品批号明细
--    对应需求规格说明书附录 表 7-1 "捕捞与养殖企业" 数据项
-- -----------------------------------------------------------------------------
DROP TABLE IF EXISTS `batch_fishing`;
CREATE TABLE `batch_fishing` (
    `id`               BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `batch_id`         BIGINT UNSIGNED NOT NULL                COMMENT '产品批号ID（product_batch.id）',
    `catch_breed_date` DATE            NOT NULL                COMMENT '捕捞或养殖日期',
    `certificate_type` TINYINT         NOT NULL                COMMENT '证明类型：1 产地检疫合格证明，2 渔获物上岸证明',
    `certificate_no`   VARCHAR(60)     NOT NULL                COMMENT '产地检疫合格证明编号或渔获物上岸证明编号',
    `departure_temp`   DECIMAL(5, 2)   NOT NULL                COMMENT '起运温度（℃），要求不高于 -18',
    `drug_report_no`   VARCHAR(60)         NULL                COMMENT '水产品药物残留检测报告编号',
    `fishing_log_no`   VARCHAR(60)         NULL                COMMENT '渔捞日志/养殖生产记录编号',
    `create_time`      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_fishing_batch` (`batch_id`),
    CONSTRAINT `fk_fishing_batch` FOREIGN KEY (`batch_id`) REFERENCES `product_batch` (`id`) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '捕捞与养殖环节产品批号明细表';


-- -----------------------------------------------------------------------------
-- 8. batch_processing  冷冻加工环节产品批号明细
--    对应需求规格说明书附录 表 7-1 "冷冻加工企业" 数据项
-- -----------------------------------------------------------------------------
DROP TABLE IF EXISTS `batch_processing`;
CREATE TABLE `batch_processing` (
    `id`                 BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `batch_id`           BIGINT UNSIGNED NOT NULL                COMMENT '产品批号ID（product_batch.id）',
    `process_form`       VARCHAR(30)     NOT NULL                COMMENT '加工形态：整鱼、去头、鱼片、鱼段、鱼块、鱼柳、虾仁等',
    `inspection_no`      VARCHAR(60)     NOT NULL                COMMENT '出厂检验报告编号',
    `quick_freeze_temp`  DECIMAL(5, 2)   NOT NULL                COMMENT '速冻中心温度（℃），要求不高于 -35',
    `factory_temp`       DECIMAL(5, 2)   NOT NULL                COMMENT '出厂温度（℃），要求不高于 -18',
    `production_batch_no` VARCHAR(60)        NULL                COMMENT '生产批次记录编号',
    `create_time`        DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`        DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_processing_batch` (`batch_id`),
    CONSTRAINT `fk_processing_batch` FOREIGN KEY (`batch_id`) REFERENCES `product_batch` (`id`) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '冷冻加工环节产品批号明细表';


-- -----------------------------------------------------------------------------
-- 9. batch_wholesale  批发与冷链储运环节产品批号明细
--    对应需求规格说明书附录 表 7-1 "批发商" 数据项；
--    批发环节同时承担冷库仓储与冷藏运输，因此一并登记入库、冷库、出库温度。
-- -----------------------------------------------------------------------------
DROP TABLE IF EXISTS `batch_wholesale`;
CREATE TABLE `batch_wholesale` (
    `id`                BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `batch_id`          BIGINT UNSIGNED NOT NULL                COMMENT '产品批号ID（product_batch.id）',
    `wholesale_date`    DATE            NOT NULL                COMMENT '批发日期',
    `inbound_temp`      DECIMAL(5, 2)   NOT NULL                COMMENT '入库温度（℃），要求不高于 -18',
    `cold_storage_temp` DECIMAL(5, 2)   NOT NULL                COMMENT '冷库温度（℃），要求不高于 -18',
    `outbound_temp`     DECIMAL(5, 2)   NOT NULL                COMMENT '出库温度（℃），要求不高于 -18',
    `transport_tool_no` VARCHAR(50)     NOT NULL                COMMENT '运输工具编号（冷藏车/冷藏船）',
    `cold_storage_no`   VARCHAR(50)         NULL                COMMENT '冷库编号',
    `create_time`       DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`       DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_wholesale_batch` (`batch_id`),
    KEY `idx_wholesale_transport` (`transport_tool_no`),
    CONSTRAINT `fk_wholesale_batch` FOREIGN KEY (`batch_id`) REFERENCES `product_batch` (`id`) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '批发与冷链储运环节产品批号明细表';


-- -----------------------------------------------------------------------------
-- 10. batch_retail  零售环节产品批号明细
--     对应需求规格说明书附录 表 7-1 "零售商" 数据项
-- -----------------------------------------------------------------------------
DROP TABLE IF EXISTS `batch_retail`;
CREATE TABLE `batch_retail` (
    `id`               BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `batch_id`         BIGINT UNSIGNED NOT NULL                COMMENT '产品批号ID（product_batch.id）',
    `shelf_date`       DATE            NOT NULL                COMMENT '上架日期',
    `display_temp`     DECIMAL(5, 2)   NOT NULL                COMMENT '冷冻陈列柜温度（℃），要求不高于 -18',
    `sale_store`       VARCHAR(100)    NOT NULL                COMMENT '销售门店',
    `display_equip_no` VARCHAR(50)         NULL                COMMENT '陈列设备编号',
    `create_time`      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_retail_batch` (`batch_id`),
    CONSTRAINT `fk_retail_batch` FOREIGN KEY (`batch_id`) REFERENCES `product_batch` (`id`) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '零售环节产品批号明细表';


-- =============================================================================
-- 五、进场确认与溯源标识码
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 11. batch_confirm_request  下游企业进场确认请求表
--     下游企业勾选"向上游企业发送确认请求"时生成一条待确认请求；
--     上游企业在"下游企业进场确认"页面按条件（进场批号为本企业批号 + 待确认）
--     查询并确认，确认后同步把下游批号状态改为"已确认"，零售商批号同时生成溯源标识码。
-- -----------------------------------------------------------------------------
DROP TABLE IF EXISTS `batch_confirm_request`;
CREATE TABLE `batch_confirm_request` (
    `id`                   BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `request_no`           VARCHAR(40)     NOT NULL                COMMENT '确认请求单号（如 FSTS-CR-20260921-0001）',
    `batch_id`             BIGINT UNSIGNED NOT NULL                COMMENT '发起方（下游）产品批号ID',
    `batch_no`             VARCHAR(60)     NOT NULL                COMMENT '发起方（下游）产品批号',
    `upstream_batch_id`    BIGINT UNSIGNED NOT NULL                COMMENT '被确认方（上游）产品批号ID',
    `upstream_batch_no`    VARCHAR(60)     NOT NULL                COMMENT '被确认方（上游）产品批号',
    `from_enterprise_id`   BIGINT UNSIGNED NOT NULL                COMMENT '发起方（下游企业）ID',
    `from_enterprise_name` VARCHAR(100)    NOT NULL                COMMENT '发起方（下游企业）名称',
    `to_enterprise_id`     BIGINT UNSIGNED NOT NULL                COMMENT '接收方（上游企业）ID',
    `to_enterprise_name`   VARCHAR(100)    NOT NULL                COMMENT '接收方（上游企业）名称',
    `handover_temp`        DECIMAL(5, 2)       NULL                COMMENT '发起方登记的交接温度（℃）',
    `request_status`       TINYINT         NOT NULL DEFAULT 1      COMMENT '请求状态：1 待确认，2 已确认，3 已拒绝，4 已撤回',
    `request_time`         DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '请求发起时间',
    `handle_time`          DATETIME            NULL                COMMENT '处理（确认/拒绝）时间',
    `handle_remark`        VARCHAR(200)        NULL                COMMENT '处理说明',
    `create_time`          DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`          DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_request_no` (`request_no`),
    UNIQUE KEY `uk_request_batch` (`batch_id`) COMMENT '同一产品批号同时只允许存在一条有效的确认请求',
    KEY `idx_request_to_enterprise` (`to_enterprise_id`, `request_status`),
    KEY `idx_request_from_enterprise` (`from_enterprise_id`),
    KEY `idx_request_upstream_batch` (`upstream_batch_id`),
    CONSTRAINT `fk_request_batch` FOREIGN KEY (`batch_id`) REFERENCES `product_batch` (`id`) ON DELETE CASCADE,
    CONSTRAINT `fk_request_upstream_batch` FOREIGN KEY (`upstream_batch_id`) REFERENCES `product_batch` (`id`),
    CONSTRAINT `fk_request_from_enterprise` FOREIGN KEY (`from_enterprise_id`) REFERENCES `node_enterprise` (`id`),
    CONSTRAINT `fk_request_to_enterprise` FOREIGN KEY (`to_enterprise_id`) REFERENCES `node_enterprise` (`id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '下游企业进场确认请求表';


-- -----------------------------------------------------------------------------
-- 12. trace_code  溯源标识码表（消费者端查询入口）
--     零售商产品批号状态变为"已确认"时由系统自动生成；
--     消费者依据标识码查询全链条溯源信息，查询次数用于统计关注度。
-- -----------------------------------------------------------------------------
DROP TABLE IF EXISTS `trace_code`;
CREATE TABLE `trace_code` (
    `id`              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `trace_code`      VARCHAR(40)     NOT NULL                COMMENT '溯源标识码（全局唯一，消费者端输入使用）',
    `batch_id`        BIGINT UNSIGNED NOT NULL                COMMENT '零售商产品批号ID',
    `batch_no`        VARCHAR(60)     NOT NULL                COMMENT '零售商产品批号',
    `retailer_id`     BIGINT UNSIGNED NOT NULL                COMMENT '零售商ID',
    `retailer_name`   VARCHAR(100)    NOT NULL                COMMENT '零售商名称',
    `product_variety` VARCHAR(50)     NOT NULL                COMMENT '产品品种',
    `sale_store`      VARCHAR(100)        NULL                COMMENT '销售门店',
    `qr_content`      VARCHAR(255)        NULL                COMMENT '二维码内容（追溯页面链接，二维码功能后续实现）',
    `status`          TINYINT         NOT NULL DEFAULT 1      COMMENT '状态：1 有效，0 失效（批号下架后同步失效）',
    `query_count`     INT             NOT NULL DEFAULT 0      COMMENT '被查询次数',
    `last_query_time` DATETIME            NULL                COMMENT '最近查询时间',
    `generate_time`   DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '生成时间',
    `create_time`     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_trace_code` (`trace_code`),
    UNIQUE KEY `uk_trace_batch` (`batch_id`),
    KEY `idx_trace_retailer` (`retailer_id`),
    CONSTRAINT `fk_trace_batch` FOREIGN KEY (`batch_id`) REFERENCES `product_batch` (`id`) ON DELETE CASCADE,
    CONSTRAINT `fk_trace_retailer` FOREIGN KEY (`retailer_id`) REFERENCES `node_enterprise` (`id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '溯源标识码表';


-- =============================================================================
-- 六、初始化数据
-- =============================================================================

-- 6.1 系统管理员
--     账号：admin        密码：123456（BCrypt 密文，登录时由
--     BCryptPasswordEncoder.matches("123456", 密文) 校验通过）
INSERT INTO `sys_admin` (`id`, `login_name`, `password`, `admin_name`, `phone`, `status`)
VALUES (1, 'admin', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVKIUi', '系统管理员', '13800000000', 1);

-- 6.2 行政区划（省级 34 条，全部）
INSERT INTO `sys_region` (`region_code`, `region_name`, `region_level`, `parent_code`, `short_name`, `sort_no`) VALUES
('110000', '北京市',           1, '000000', '北京',   1),
('120000', '天津市',           1, '000000', '天津',   2),
('130000', '河北省',           1, '000000', '河北',   3),
('140000', '山西省',           1, '000000', '山西',   4),
('150000', '内蒙古自治区',     1, '000000', '内蒙古', 5),
('210000', '辽宁省',           1, '000000', '辽宁',   6),
('220000', '吉林省',           1, '000000', '吉林',   7),
('230000', '黑龙江省',         1, '000000', '黑龙江', 8),
('310000', '上海市',           1, '000000', '上海',   9),
('320000', '江苏省',           1, '000000', '江苏',  10),
('330000', '浙江省',           1, '000000', '浙江',  11),
('340000', '安徽省',           1, '000000', '安徽',  12),
('350000', '福建省',           1, '000000', '福建',  13),
('360000', '江西省',           1, '000000', '江西',  14),
('370000', '山东省',           1, '000000', '山东',  15),
('410000', '河南省',           1, '000000', '河南',  16),
('420000', '湖北省',           1, '000000', '湖北',  17),
('430000', '湖南省',           1, '000000', '湖南',  18),
('440000', '广东省',           1, '000000', '广东',  19),
('450000', '广西壮族自治区',   1, '000000', '广西',  20),
('460000', '海南省',           1, '000000', '海南',  21),
('500000', '重庆市',           1, '000000', '重庆',  22),
('510000', '四川省',           1, '000000', '四川',  23),
('520000', '贵州省',           1, '000000', '贵州',  24),
('530000', '云南省',           1, '000000', '云南',  25),
('540000', '西藏自治区',       1, '000000', '西藏',  26),
('610000', '陕西省',           1, '000000', '陕西',  27),
('620000', '甘肃省',           1, '000000', '甘肃',  28),
('630000', '青海省',           1, '000000', '青海',  29),
('640000', '宁夏回族自治区',   1, '000000', '宁夏',  30),
('650000', '新疆维吾尔自治区', 1, '000000', '新疆',  31),
('710000', '台湾省',           1, '000000', '台湾',  32),
('810000', '香港特别行政区',   1, '000000', '香港',  33),
('820000', '澳门特别行政区',   1, '000000', '澳门',  34);

-- 6.3 行政区划（市级：直辖市与主要沿海水产省，可自行补充其余省份城市数据）
INSERT INTO `sys_region` (`region_code`, `region_name`, `region_level`, `parent_code`, `sort_no`) VALUES
-- 北京市、天津市、上海市、重庆市
('110100', '北京市',     2, '110000', 1),
('120100', '天津市',     2, '120000', 1),
('310100', '上海市',     2, '310000', 1),
('500100', '重庆市',     2, '500000', 1),
-- 辽宁省
('210100', '沈阳市',     2, '210000', 1),
('210200', '大连市',     2, '210000', 2),
('210300', '鞍山市',     2, '210000', 3),
('210400', '抚顺市',     2, '210000', 4),
('210500', '本溪市',     2, '210000', 5),
('210600', '丹东市',     2, '210000', 6),
('210700', '锦州市',     2, '210000', 7),
('210800', '营口市',     2, '210000', 8),
('210900', '阜新市',     2, '210000', 9),
('211000', '辽阳市',     2, '210000', 10),
('211100', '盘锦市',     2, '210000', 11),
('211200', '铁岭市',     2, '210000', 12),
('211300', '朝阳市',     2, '210000', 13),
('211400', '葫芦岛市',   2, '210000', 14),
-- 河北省
('130100', '石家庄市',   2, '130000', 1),
('130200', '唐山市',     2, '130000', 2),
('130300', '秦皇岛市',   2, '130000', 3),
('130400', '邯郸市',     2, '130000', 4),
('130500', '邢台市',     2, '130000', 5),
('130600', '保定市',     2, '130000', 6),
('130700', '张家口市',   2, '130000', 7),
('130800', '承德市',     2, '130000', 8),
('130900', '沧州市',     2, '130000', 9),
('131000', '廊坊市',     2, '130000', 10),
('131100', '衡水市',     2, '130000', 11),
-- 江苏省
('320100', '南京市',     2, '320000', 1),
('320200', '无锡市',     2, '320000', 2),
('320300', '徐州市',     2, '320000', 3),
('320400', '常州市',     2, '320000', 4),
('320500', '苏州市',     2, '320000', 5),
('320600', '南通市',     2, '320000', 6),
('320700', '连云港市',   2, '320000', 7),
('320800', '淮安市',     2, '320000', 8),
('320900', '盐城市',     2, '320000', 9),
('321000', '扬州市',     2, '320000', 10),
('321100', '镇江市',     2, '320000', 11),
('321200', '泰州市',     2, '320000', 12),
('321300', '宿迁市',     2, '320000', 13),
-- 浙江省
('330100', '杭州市',     2, '330000', 1),
('330200', '宁波市',     2, '330000', 2),
('330300', '温州市',     2, '330000', 3),
('330400', '嘉兴市',     2, '330000', 4),
('330500', '湖州市',     2, '330000', 5),
('330600', '绍兴市',     2, '330000', 6),
('330700', '金华市',     2, '330000', 7),
('330800', '衢州市',     2, '330000', 8),
('330900', '舟山市',     2, '330000', 9),
('331000', '台州市',     2, '330000', 10),
('331100', '丽水市',     2, '330000', 11),
-- 福建省
('350100', '福州市',     2, '350000', 1),
('350200', '厦门市',     2, '350000', 2),
('350300', '莆田市',     2, '350000', 3),
('350400', '三明市',     2, '350000', 4),
('350500', '泉州市',     2, '350000', 5),
('350600', '漳州市',     2, '350000', 6),
('350700', '南平市',     2, '350000', 7),
('350800', '龙岩市',     2, '350000', 8),
('350900', '宁德市',     2, '350000', 9),
-- 山东省
('370100', '济南市',     2, '370000', 1),
('370200', '青岛市',     2, '370000', 2),
('370300', '淄博市',     2, '370000', 3),
('370400', '枣庄市',     2, '370000', 4),
('370500', '东营市',     2, '370000', 5),
('370600', '烟台市',     2, '370000', 6),
('370700', '潍坊市',     2, '370000', 7),
('370800', '济宁市',     2, '370000', 8),
('370900', '泰安市',     2, '370000', 9),
('371000', '威海市',     2, '370000', 10),
('371100', '日照市',     2, '370000', 11),
('371300', '临沂市',     2, '370000', 12),
('371400', '德州市',     2, '370000', 13),
('371500', '聊城市',     2, '370000', 14),
('371600', '滨州市',     2, '370000', 15),
('371700', '菏泽市',     2, '370000', 16),
-- 广东省
('440100', '广州市',     2, '440000', 1),
('440200', '韶关市',     2, '440000', 2),
('440300', '深圳市',     2, '440000', 3),
('440400', '珠海市',     2, '440000', 4),
('440500', '汕头市',     2, '440000', 5),
('440600', '佛山市',     2, '440000', 6),
('440700', '江门市',     2, '440000', 7),
('440800', '湛江市',     2, '440000', 8),
('440900', '茂名市',     2, '440000', 9),
('441200', '肇庆市',     2, '440000', 10),
('441300', '惠州市',     2, '440000', 11),
('441400', '梅州市',     2, '440000', 12),
('441500', '汕尾市',     2, '440000', 13),
('441600', '河源市',     2, '440000', 14),
('441700', '阳江市',     2, '440000', 15),
('441800', '清远市',     2, '440000', 16),
('441900', '东莞市',     2, '440000', 17),
('442000', '中山市',     2, '440000', 18),
('445100', '潮州市',     2, '440000', 19),
('445200', '揭阳市',     2, '440000', 20),
('445300', '云浮市',     2, '440000', 21),
-- 广西壮族自治区
('450100', '南宁市',     2, '450000', 1),
('450200', '柳州市',     2, '450000', 2),
('450300', '桂林市',     2, '450000', 3),
('450400', '梧州市',     2, '450000', 4),
('450500', '北海市',     2, '450000', 5),
('450600', '防城港市',   2, '450000', 6),
('450700', '钦州市',     2, '450000', 7),
('450800', '贵港市',     2, '450000', 8),
('450900', '玉林市',     2, '450000', 9),
('451000', '百色市',     2, '450000', 10),
('451100', '贺州市',     2, '450000', 11),
('451200', '河池市',     2, '450000', 12),
('451300', '来宾市',     2, '450000', 13),
('451400', '崇左市',     2, '450000', 14),
-- 海南省
('460100', '海口市',     2, '460000', 1),
('460200', '三亚市',     2, '460000', 2),
('460300', '三沙市',     2, '460000', 3),
('460400', '儋州市',     2, '460000', 4);

-- 6.4 数据字典类型
INSERT INTO `sys_dict_type` (`type_code`, `type_name`, `remark`) VALUES
('enterprise_type', '节点企业类型',     '系统管理端节点企业注册信息管理使用'),
('source_type',     '产品来源类型',     '捕捞与养殖企业新建产品批号使用'),
('certificate_type','来源证明材料类型', '产地检疫合格证明 / 渔获物上岸证明'),
('process_form',    '加工形态',         '冷冻加工企业新建产品批号使用'),
('product_variety', '产品品种',         '各环节产品品种下拉候选，可按业务扩展'),
('batch_status',    '产品批号状态',     '新建/待发布、待确认、已确认/已发布、已下架'),
('request_status',  '确认请求状态',     '待确认、已确认、已拒绝、已撤回');

-- 6.5 数据字典项
INSERT INTO `sys_dict_item` (`type_code`, `item_code`, `item_value`, `sort_no`, `remark`) VALUES
-- 节点企业类型
('enterprise_type', '1', '捕捞与养殖企业', 1, '链条源头：捕捞、养殖'),
('enterprise_type', '2', '冷冻加工企业',   2, '冷冻加工'),
('enterprise_type', '3', '批发商',         3, '承担冷库仓储与冷藏运输'),
('enterprise_type', '4', '零售商',         4, '链条末端，向消费者展示溯源标识码'),
-- 产品来源类型
('source_type', '1', '养殖', 1, NULL),
('source_type', '2', '捕捞', 2, NULL),
-- 来源证明材料类型
('certificate_type', '1', '产地检疫合格证明', 1, '养殖产品'),
('certificate_type', '2', '渔获物上岸证明',   2, '捕捞产品'),
-- 加工形态
('process_form', 'WHOLE',    '整鱼',  1, NULL),
('process_form', 'HEADLESS', '去头',  2, NULL),
('process_form', 'FILLET',   '鱼片',  3, NULL),
('process_form', 'SECTION',  '鱼段',  4, NULL),
('process_form', 'BLOCK',    '鱼块',  5, NULL),
('process_form', 'LOIN',     '鱼柳',  6, NULL),
('process_form', 'SHRIMP',   '虾仁',  7, NULL),
('process_form', 'OTHER',    '其他',  99, NULL),
-- 产品品种
('product_variety', 'DaiYu',      '带鱼',       1,  NULL),
('product_variety', 'XiaoHuangYu','小黄鱼',     2,  NULL),
('product_variety', 'DaHuangYu',  '大黄鱼',     3,  NULL),
('product_variety', 'ChangYu',    '鲳鱼',       4,  NULL),
('product_variety', 'XueYu',      '鳕鱼',       5,  NULL),
('product_variety', 'SanWenYu',   '三文鱼',     6,  NULL),
('product_variety', 'JinQiangYu', '金枪鱼',     7,  NULL),
('product_variety', 'BaYu',       '鲅鱼',       8,  NULL),
('product_variety', 'LuYu',       '鲈鱼',       9,  NULL),
('product_variety', 'ShiBanYu',   '石斑鱼',     10, NULL),
('product_variety', 'YouYu',      '鱿鱼',       11, NULL),
('product_variety', 'ZhangYu',    '章鱼',       12, NULL),
('product_variety', 'DuiXia',     '对虾',       13, NULL),
('product_variety', 'NanMeiBai',  '南美白对虾', 14, NULL),
('product_variety', 'SuoZiXie',   '梭子蟹',     15, NULL),
('product_variety', 'BaoYu',      '鲍鱼',       16, NULL),
('product_variety', 'ShanBei',    '扇贝',       17, NULL),
('product_variety', 'HaiShen',    '海参',       18, NULL),
('product_variety', 'MoYu',       '墨鱼',       19, NULL),
('product_variety', 'QiuDaoYu',   '秋刀鱼',     20, NULL),
('product_variety', 'DuoChunYu',  '多春鱼',     21, NULL),
-- 产品批号状态
('batch_status', '1', '新建',  1, '捕捞与养殖企业显示为"待发布"'),
('batch_status', '2', '待确认', 2, '已向上游企业发送确认请求，等待上游确认'),
('batch_status', '3', '已确认', 3, '捕捞与养殖企业发布后即为该状态，可供下游选择'),
('batch_status', '4', '已下架', 4, '下架后不可浏览、不可被下游选择'),
-- 确认请求状态
('request_status', '1', '待确认', 1, NULL),
('request_status', '2', '已确认', 2, NULL),
('request_status', '3', '已拒绝', 3, NULL),
('request_status', '4', '已撤回', 4, NULL);


-- =============================================================================
-- 七、可选视图（便于统计与溯源链查询，可按需删除）
-- =============================================================================

-- 7.1 以省为分组统计节点企业注册数量（可视化大屏：饼图 / 柱状图）
CREATE OR REPLACE VIEW `v_enterprise_province_stat` AS
SELECT r.`province_code`                        AS `province_code`,
       r.`province_name`                        AS `province_name`,
       COUNT(r.`id`)                            AS `enterprise_count`,
       SUM(CASE WHEN r.`enterprise_type` = 1 THEN 1 ELSE 0 END) AS `fishing_count`,
       SUM(CASE WHEN r.`enterprise_type` = 2 THEN 1 ELSE 0 END) AS `processing_count`,
       SUM(CASE WHEN r.`enterprise_type` = 3 THEN 1 ELSE 0 END) AS `wholesale_count`,
       SUM(CASE WHEN r.`enterprise_type` = 4 THEN 1 ELSE 0 END) AS `retail_count`
FROM `node_enterprise` r
WHERE r.`deleted` = 0
GROUP BY r.`province_code`, r.`province_name`;

-- 7.2 以企业类型为分组统计注册数量分布（可视化大屏：饼图）
CREATE OR REPLACE VIEW `v_enterprise_type_stat` AS
SELECT r.`enterprise_type` AS `enterprise_type`,
       d.`item_value`      AS `enterprise_type_name`,
       COUNT(r.`id`)       AS `enterprise_count`
FROM `node_enterprise` r
LEFT JOIN `sys_dict_item` d
       ON d.`type_code` = 'enterprise_type'
      AND d.`item_code` = CAST(r.`enterprise_type` AS CHAR)
WHERE r.`deleted` = 0
GROUP BY r.`enterprise_type`, d.`item_value`;

-- 7.3 产品批号溯源链视图（消费者端按链条顺序展示）
--     说明：批号通过 upstream_batch_id 自关联形成链条，
--          stage_order 按"捕捞与养殖 -> 冷冻加工 -> 批发 -> 零售"排序。
CREATE OR REPLACE VIEW `v_batch_trace_chain` AS
SELECT b.`id`                     AS `batch_id`,
       b.`batch_no`               AS `batch_no`,
       b.`enterprise_id`          AS `enterprise_id`,
       b.`enterprise_name`        AS `enterprise_name`,
       b.`enterprise_type`        AS `enterprise_type`,
       d.`item_value`             AS `enterprise_type_name`,
       b.`upstream_batch_id`      AS `upstream_batch_id`,
       b.`upstream_batch_no`      AS `upstream_batch_no`,
       b.`product_variety`        AS `product_variety`,
       b.`source_type`            AS `source_type`,
       b.`batch_status`           AS `batch_status`,
       b.`handover_temp`          AS `handover_temp`,
       b.`cold_chain_ok`          AS `cold_chain_ok`,
       b.`publish_time`           AS `publish_time`,
       b.`off_shelf_time`         AS `off_shelf_time`,
       b.`create_time`            AS `create_time`
FROM `product_batch` b
LEFT JOIN `sys_dict_item` d
       ON d.`type_code` = 'enterprise_type'
      AND d.`item_code` = CAST(b.`enterprise_type` AS CHAR)
WHERE b.`deleted` = 0;


SET FOREIGN_KEY_CHECKS = 1;

-- =============================================================================
-- 脚本执行完毕
-- 1. 执行顺序：先执行本脚本（建库建表 + 基础数据），
--    再执行《冷冻海产品溯源系统-演示数据脚本.sql》（可选）。
-- 2. 若目标环境不允许使用外键，可删除各表的 CONSTRAINT 子句，
--    改由业务层保证引用完整性，其余语句不受影响。
-- 3. 统计接口对应的 SQL 见《冷冻海产品溯源系统-前后端接口文档.md》附录 C。
-- =============================================================================
