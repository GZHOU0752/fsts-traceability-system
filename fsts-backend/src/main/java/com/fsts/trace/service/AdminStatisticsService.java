package com.fsts.trace.service;

import com.fsts.trace.config.props.CacheProperties;
import com.fsts.trace.dto.stat.BusinessMetricRow;
import com.fsts.trace.dto.stat.OverviewRow;
import com.fsts.trace.dto.stat.ProvinceStatRow;
import com.fsts.trace.dto.stat.RegisterTrendRow;
import com.fsts.trace.dto.stat.TypeStatRow;
import com.fsts.trace.mapper.AdminStatisticsMapper;
import com.fsts.trace.mapper.NodeEnterpriseMapper;
import com.fsts.trace.support.cache.CacheService;
import com.fsts.trace.vo.BusinessMetricsVO;
import com.fsts.trace.vo.OverviewVO;
import com.fsts.trace.vo.ProvinceCountVO;
import com.fsts.trace.vo.ProvinceDistributionVO;
import com.fsts.trace.vo.RegisterTrendVO;
import com.fsts.trace.vo.TypeDistributionVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * 系统管理端 - 可视化大屏统计（接口 7.1 ~ 7.6）。
 *
 * <p><b>缓存策略：</b>大屏初始化时会并行调用 6 个聚合接口，而聚合结果对"秒级延迟"
 * 并不敏感（数据大屏本来就不是实时交易系统），因此统一加短 TTL 缓存
 * （默认 30 秒，见 {@code fsts.cache.statistics-ttl-seconds}）。
 * 企业新增/修改/删除会主动失效整个命名空间，避免出现"刚录完企业、大屏还是旧数字"。
 */
@Slf4j
@Service
public class AdminStatisticsService {

    private static final String NAMESPACE = "statistics";
    private static final DateTimeFormatter MONTH_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM");
    private static final int DEFAULT_TREND_MONTHS = 12;
    private static final int MAX_TREND_MONTHS = 36;

    private final NodeEnterpriseMapper enterpriseMapper;
    private final AdminStatisticsMapper statisticsMapper;
    private final CacheService cacheService;
    private final CacheProperties cacheProperties;

    public AdminStatisticsService(NodeEnterpriseMapper enterpriseMapper,
                                  AdminStatisticsMapper statisticsMapper,
                                  CacheService cacheService,
                                  CacheProperties cacheProperties) {
        this.enterpriseMapper = enterpriseMapper;
        this.statisticsMapper = statisticsMapper;
        this.cacheService = cacheService;
        this.cacheProperties = cacheProperties;
    }

    /**
     * 大屏概览卡片（接口 7.1）。
     */
    public OverviewVO overview() {
        return cachedValue("overview", OverviewVO.class, () -> {
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
        });
    }

    /**
     * 业务指标卡片（接口 7.6）。
     *
     * <p>与 7.1 的企业维度互补：7.1 回答"平台上有多少家企业"，
     * 这里回答"平台上正在发生什么"——在架批号、冷链异常、确认请求积压。
     * 后者才是运营视角下真正需要盯的数字。
     */
    public BusinessMetricsVO businessMetrics() {
        return cachedValue("business", BusinessMetricsVO.class, () -> {
            BusinessMetricRow row = statisticsMapper.selectBusinessMetrics();
            return BusinessMetricsVO.builder()
                    .totalBatchCount(nullToZero(row.getTotalBatchCount()))
                    .activeBatchCount(nullToZero(row.getActiveBatchCount()))
                    .coldChainAbnormalCount(nullToZero(row.getColdChainAbnormalCount()))
                    .pendingConfirmCount(nullToZero(row.getPendingConfirmCount()))
                    .validTraceCodeCount(nullToZero(row.getValidTraceCodeCount()))
                    .updateTime(LocalDateTime.now())
                    .build();
        });
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
        return cachedValue("trend:" + span, RegisterTrendVO.class, () -> {
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
            return RegisterTrendVO.builder().months(monthAxis).counts(counts).total(total).build();
        });
    }

    /**
     * 省分组分布（接口 7.3，饼图），支持只取前 top 名。
     */
    public List<ProvinceDistributionVO> provinceDistribution(Integer top) {
        int limit = top == null || top <= 0 ? 0 : top;
        return cachedList("province:" + limit, ProvinceDistributionVO.class, () -> {
            List<ProvinceStatRow> rows = enterpriseMapper.selectProvinceStat();
            long total = rows.stream().mapToLong(r -> nullToZero(r.getCnt())).sum();
            int size = limit <= 0 ? rows.size() : Math.min(limit, rows.size());
            List<ProvinceDistributionVO> result = new ArrayList<>(size);
            for (int i = 0; i < size; i++) {
                ProvinceStatRow row = rows.get(i);
                result.add(ProvinceDistributionVO.builder()
                        .provinceCode(row.getProvinceCode())
                        .provinceName(row.getProvinceName())
                        .count(nullToZero(row.getCnt()))
                        .percent(percent(nullToZero(row.getCnt()), total))
                        .build());
            }
            return result;
        });
    }

    /**
     * 省分组数量（接口 7.4，柱状图）：返回全部省份，按数量降序。
     */
    public ProvinceCountVO provinceCount() {
        return cachedValue("province-count", ProvinceCountVO.class, () -> {
            List<ProvinceStatRow> rows = enterpriseMapper.selectProvinceStat();
            List<String> provinces = new ArrayList<>(rows.size());
            List<Long> counts = new ArrayList<>(rows.size());
            for (ProvinceStatRow row : rows) {
                provinces.add(row.getProvinceName());
                counts.add(nullToZero(row.getCnt()));
            }
            return ProvinceCountVO.builder().provinces(provinces).counts(counts).build();
        });
    }

    /**
     * 企业类型分布（接口 7.5，饼图）。
     *
     * <p>注意：这里按数据库中实际存在的类型返回，不强行补齐 0 值的类型。
     * 若业务要求四类固定展示，可在此处按 1~4 补全 —— 当前按接口文档示例语义（只列有数据的类型）实现。
     */
    public List<TypeDistributionVO> typeDistribution() {
        return cachedList("type-distribution", TypeDistributionVO.class, () -> {
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
        });
    }

    /**
     * 主动失效大屏缓存：企业数据变更后调用，保证"刚录完就能在大屏看到"。
     */
    public void evictCache() {
        cacheService.evictNamespace(NAMESPACE);
    }

    // ------------------------------------------------------------------
    // 内部方法
    // ------------------------------------------------------------------

    private <T> T cachedValue(String key, Class<T> type, Supplier<T> loader) {
        int ttl = cacheProperties.getStatisticsTtlSeconds();
        if (ttl <= 0) {
            return loader.get();
        }
        T cached = cacheService.get(NAMESPACE, key, type);
        if (cached != null) {
            return cached;
        }
        T value = loader.get();
        cacheService.put(NAMESPACE, key, value, Duration.ofSeconds(ttl));
        return value;
    }

    private <T> List<T> cachedList(String key, Class<T> elementType, Supplier<List<T>> loader) {
        int ttl = cacheProperties.getStatisticsTtlSeconds();
        if (ttl <= 0) {
            return loader.get();
        }
        List<T> cached = cacheService.getList(NAMESPACE, key, elementType);
        if (cached != null) {
            return cached;
        }
        List<T> value = loader.get();
        cacheService.put(NAMESPACE, key, value, Duration.ofSeconds(ttl));
        return value;
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
