package com.fsts.trace.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fsts.trace.dto.stat.OverviewRow;
import com.fsts.trace.dto.stat.ProvinceStatRow;
import com.fsts.trace.dto.stat.RegisterTrendRow;
import com.fsts.trace.dto.stat.TypeStatRow;
import com.fsts.trace.entity.NodeEnterprise;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * 节点企业 Mapper。
 *
 * <p>提示：统计类 SQL 全部手写在 XML 中并显式带上 {@code deleted = 0}，
 * 因为 {@code @TableLogic} 只对 MyBatis-Plus 自动生成的 SQL 生效。
 */
@Mapper
public interface NodeEnterpriseMapper extends BaseMapper<NodeEnterprise> {

    /**
     * 大屏概览（接口 7.1）：一次扫描出全部卡片指标，避免多次 round-trip。
     */
    OverviewRow selectOverview();

    /**
     * 自 {@code startMonth}（含）起的月度注册趋势（接口 7.2）。
     *
     * <p>只返回有数据的月份，空缺月份由服务层补 0 —— 起始月份在 Java 侧算好再传入，
     * 避免把 {@code INTERVAL} 的月份数拼接进 SQL（防注入、也保证索引可用）。
     */
    List<RegisterTrendRow> selectRegisterTrend(@Param("startMonth") java.time.LocalDate startMonth);

    /**
     * 省分组注册数量（接口 7.3 / 7.4），按数量倒序。
     */
    List<ProvinceStatRow> selectProvinceStat();

    /**
     * 企业类型分布（接口 7.5）。
     */
    List<TypeStatRow> selectTypeStat();

    /**
     * 复活被逻辑删除的企业记录。
     *
     * <p>原因：{@code node_enterprise} 的 {@code login_name} / {@code credit_code} /
     * {@code enterprise_code} 均为唯一索引，逻辑删除的记录仍占用索引。
     * 删除后重新注册同一家企业时必须复用原记录（deleted 置回 0）而非新增。
     */
    @Update("UPDATE node_enterprise SET deleted = 0, status = 1, update_time = NOW() WHERE id = #{id}")
    int reviveById(@Param("id") Long id);

    /**
     * 查找被逻辑删除的历史记录（按登录账号或统一社会信用代码匹配）。
     *
     * <p>为什么需要它：逻辑删除的行仍占用唯一索引，
     * 删除后重新注册同一家企业若直接 INSERT 必然撞唯一键。
     * 正确做法是复用原行（覆盖数据并把 deleted 置回 0）。
     * 此处绕过了 @TableLogic 的自动过滤，故必须显式写 deleted = 1。
     */
    @Select("""
            SELECT * FROM node_enterprise
             WHERE deleted = 1
               AND (login_name = #{loginName} OR credit_code = #{creditCode})
             ORDER BY id DESC
             LIMIT 1
            """)
    NodeEnterprise selectDeletedByLoginNameOrCreditCode(@Param("loginName") String loginName,
                                                        @Param("creditCode") String creditCode);

    /**
     * 判断除指定 ID 外是否已存在同值字段（用于唯一性校验接口 6.6）。
     */
    @Select("""
            <script>
            SELECT COUNT(1) FROM node_enterprise
            WHERE deleted = 0
              AND ${column} = #{value}
              <if test="excludeId != null"> AND id &lt;&gt; #{excludeId} </if>
            </script>
            """)
    long countByColumn(@Param("column") String column, @Param("value") String value,
                       @Param("excludeId") Long excludeId);

    /**
     * 按 ID 批量查询企业（<b>包含已逻辑删除的记录</b>）。
     *
     * <p>消费者端溯源必须能展示历史链条上的企业名称与所在地：
     * 接口 6.5 业务规则 2 明确"企业逻辑删除后，其历史批次在消费者端溯源查询中仍可正常展示"。
     * MyBatis-Plus 的 selectBatchIds 会被 {@code @TableLogic} 过滤掉已删除行，
     * 因此这里必须用显式 SQL 绕过，否则溯源页会出现企业信息为空。
     */
    @Select("""
            <script>
            SELECT id, enterprise_code, enterprise_name, enterprise_type, province_code, province_name,
                   city_code, city_name, status
              FROM node_enterprise
             WHERE id IN
             <foreach collection="ids" item="id" open="(" separator="," close=")">#{id}</foreach>
            </script>
            """)
    List<NodeEnterprise> selectByIdsIncludeDeleted(@Param("ids") List<Long> ids);
}
