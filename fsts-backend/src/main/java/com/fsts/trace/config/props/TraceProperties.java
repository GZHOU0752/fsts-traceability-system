package com.fsts.trace.config.props;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * 溯源相关配置（fsts.trace.*）。
 */
@Data
@Component
@ConfigurationProperties(prefix = "fsts.trace")
public class TraceProperties {

    /** 二维码内容前缀，最终拼接为 {qrBaseUrl}{traceCode} */
    private String qrBaseUrl = "https://fsts.example.com/trace/";

    /** 查询次数聚合落库间隔（毫秒） */
    private long queryCountFlushIntervalMs = 5000L;

    /** 单次聚合落库的最大条数 */
    private int queryCountFlushBatch = 500;

    /**
     * 冷冻海产品通用温度阈值（℃）：不高于该值判定合格。
     *
     * <p>默认 -18 来自 GB 31605-2020（冷冻食品贮存、运输、交接应保持不高于 -18 ℃）。
     * 做成配置项的原因：不同品类判据并不相同（例如金枪鱼需 -60 ℃ 深冷），
     * 把阈值写死在常量里会让系统无法适配新的品类与地方标准。
     */
    private BigDecimal normalTempThreshold = new BigDecimal("-18.00");

    /** 速冻中心温度阈值（℃）：加工环节速冻工序的判据，默认 -35 */
    private BigDecimal quickFreezeTempThreshold = new BigDecimal("-35.00");

    /**
     * 消费者端溯源结果的缓存时长（秒），默认 60；设为 0 表示关闭缓存。
     *
     * <p>溯源结果是"读多写极少"的数据，但一次查询要跑 1 次标识码 + 1 次递归链 +
     * 至多 4 次明细查询，热点溯源码（例如被投放到社群里的批次）会反复打同一批行。
     * 批号下架/恢复上架时由 TraceCodeService 主动失效，保证不会出现"已下架还能查"。
     */
    private int resultTtlSeconds = 60;

    /**
     * 是否在溯源码末尾追加校验位（默认开启）。
     *
     * <p>开启后新生成的溯源码形如 {@code FSTS-20260915-SH-0001-7}，
     * 手输错一位时可直接判定"校验位不正确"；历史数据（不含校验位）仍然可以正常查询。
     */
    private boolean checkDigitEnabled = true;
}
