package com.fsts.trace.service;

import com.fsts.trace.dto.stat.OverviewRow;
import com.fsts.trace.dto.stat.ProvinceStatRow;
import com.fsts.trace.dto.stat.RegisterTrendRow;
import com.fsts.trace.dto.stat.TypeStatRow;
import com.fsts.trace.mapper.NodeEnterpriseMapper;
import com.fsts.trace.vo.OverviewVO;
import com.fsts.trace.vo.ProvinceCountVO;
import com.fsts.trace.vo.ProvinceDistributionVO;
import com.fsts.trace.vo.RegisterTrendVO;
import com.fsts.trace.vo.TypeDistributionVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 系统管理端 - 可视化大屏统计（接口 7.1 ~ 7.5）。
 *
 * <p>性能说明：五个接口都是聚合查询，数据量随企业数增长而增长。
 * 当前实现全部走数据库索引做实时聚合，在企业数达到十万级之前都足够快。
 * 若未来成为瓶颈，可在此层加 30 秒结果缓存（数据大屏本就容忍秒级延迟），
 * 因为 {@link #overview()} 等方法的返回结构是稳定的值对象，加缓存不影响契约。
 */
@Slf4j
@Service
public class AdminStatisticsService {

    private static final DateTimeFormatter MONTH_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM");
    private static final int DEFAULT_TREND_MONTHS = 12;
    private static final int MAX_TREND_MONTHS = 36;

    private final NodeEnterpriseMapper enterpriseMapper;

    public AdminStatisticsService(NodeEnterpriseMapper enterpriseMapper) {
        this.enterpriseMapper = enterpriseMapper;
    }

    /**
     * 大屏概览卡片（接口 7.1）。
     */
    public OverviewVO overview() {
        OverviewRow row = enterpriseMapper.selectOverview();
        return OverviewVO.builder()
                .totalCount(nullToZero(row.getTotalCount()))
                .fishingCount(nullToZero(row.getFishingCount()))
                .processingCount(nullToZero(row.getProcessingCount()))
                .wholesaleCount(nullToZero(row.getWholesaleCount()))
                .retailCount(nullToZero(row.getRetailCount()))
                .provinceCount(nullToZero(row.getProvinceCount()))
                .todayRegisterCount(nullToZero(row.getTodayRegisterCount()))
                .updateTime(LocalDateTime.now())
                .build();
    }

    /**
     * 十二个月注册趋势（接口 7.2）。
     *
     * <p>关键点：横轴必须由服务层生成连续月份并补 0，
     * 不能直接把数据库分组结果当横轴 —— 否则没有注册记录的月份会缺失，
     * 折线图横轴断裂、X 轴刻度错位。
     */
    public RegisterTrendVO registerTrend(Integer months) {
        int span = normalizeMonths(months);
        YearMonth current = YearMonth.now();

        List<String> monthAxis = new ArrayList<>(span);
        for (int i = span - 1; i >= 0; i--) {
            monthAxis.add(current.minusMonths(i).format(MONTH_FORMATTER));
        }

        YearMonth startMonth = current.minusMonths(span - 1L);
        List<RegisterTrendRow> rows = enterpriseMapper.selectRegisterTrend(startMonth.atDay(1));

        Map<String, Long> countMap = new HashMap<>(rows.size() * 2);
        for (RegisterTrendRow row : rows) {
            countMap.put(row.getMonth(), nullToZero(row.getCnt()));
        }

        List<Long> counts = new ArrayList<>(span);
        long total = 0L;
        for (String month : monthAxis) {
            Long count = countMap.getOrDefault(month, 0L);
            counts.add(count);
            total += count;
        }

        return RegisterTrendVO.builder()
                .months(monthAxis)
                .counts(counts)
                .total(total)
                .build();
    }

    /**
     * 省分组分布（接口 7.3，饼图），支持只取前 top 名。
     */
    public List<ProvinceDistributionVO> provinceDistribution(Integer top) {
        List<ProvinceStatRow> rows = enterpriseMapper.selectProvinceStat();
        long total = rows.stream().mapToLong(r -> nullToZero(r.getCnt())).sum();

        int limit = (top == null || top <= 0) ? rows.size() : Math.min(top, rows.size());
        List<ProvinceDistributionVO> result = new ArrayList<>(limit);
        for (int i = 0; i < limit; i++) {
            ProvinceStatRow row = rows.get(i);
            result.add(ProvinceDistributionVO.builder()
                    .provinceCode(row.getProvinceCode())
                    .provinceName(row.getProvinceName())
                    .count(nullToZero(row.getCnt()))
                    .percent(percent(nullToZero(row.getCnt()), total))
                    .build());
        }
        return result;
    }

    /**
     * 省分组数量（接口 7.4，柱状图）：返回全部省份，按数量降序。
     */
    public ProvinceCountVO provinceCount() {
        List<ProvinceStatRow> rows = enterpriseMapper.selectProvinceStat();
        List<String> provinces = new ArrayList<>(rows.size());
        List<Long> counts = new ArrayList<>(rows.size());
        for (ProvinceStatRow row : rows) {
            provinces.add(row.getProvinceName());
            counts.add(nullToZero(row.getCnt()));
        }
        return ProvinceCountVO.builder().provinces(provinces).counts(counts).build();
    }

    /**
     * 企业类型分布（接口 7.5，饼图）。
     *
     * <p>注意：这里按数据库中实际存在的类型返回，不强行补齐 0 值的类型。
     * 若业务要求四类固定展示，可在此处按 1~4 补全 —— 当前按接口文档示例语义（只列有数据的类型）实现。
     */
    public List<TypeDistributionVO> typeDistribution() {
        List<TypeStatRow> rows = enterpriseMapper.selectTypeStat();
        long total = rows.stream().mapToLong(r -> nullToZero(r.getCnt())).sum();

        List<TypeDistributionVO> result = new ArrayList<>(rows.size());
        for (TypeStatRow row : rows) {
            result.add(TypeDistributionVO.builder()
                    .enterpriseType(row.getEnterpriseType())
                    .enterpriseTypeName(row.getEnterpriseTypeName())
                    .count(nullToZero(row.getCnt()))
                    .percent(percent(nullToZero(row.getCnt()), total))
                    .build());
        }
        return result;
    }

    private int normalizeMonths(Integer months) {
        if (months == null || months <= 0) {
            return DEFAULT_TREND_MONTHS;
        }
        return Math.min(months, MAX_TREND_MONTHS);
    }

    private Long nullToZero(Long value) {
        return value == null ? 0L : value;
    }

    /**
     * 占比计算：保留 2 位小数，四舍五入；总量为 0 时返回 0.00。
     */
    private BigDecimal percent(long count, long total) {
        if (total <= 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return BigDecimal.valueOf(count)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(total), 2, RoundingMode.HALF_UP);
    }
}
