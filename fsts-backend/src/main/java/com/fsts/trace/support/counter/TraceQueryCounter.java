package com.fsts.trace.support.counter;

import com.fsts.trace.mapper.TraceCodeMapper;
import com.fsts.trace.config.props.TraceProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 溯源码查询次数聚合器。
 *
 * <p><b>为什么不在读路径直接 UPDATE：</b>
 * 消费者端每次查询都要 query_count + 1，如果同步写库，
 * 一个爆款批次的标识码会变成"单行热点"，所有查询在该行上串行排队等锁，
 * 读接口的反而是被写操作拖慢甚至拖垮 —— 这是典型的读写互相拖累。
 *
 * <p><b>解决方式：</b>内存聚合 + 定时批量落库。
 * 读路径只做一次无锁的原子自增，耗时纳秒级；
 * 后台每 5 秒（可配置）把这段时间的增量一次性写回数据库，
 * 数据库写入量从"每查询 1 次"降到"每条标识码每 5 秒最多 1 次"。
 *
 * <p><b>代价：</b>进程被强杀时会丢失最多一个刷新周期的增量。
 * 对"展示关注度"这类非事务性统计指标，这个代价完全可以接受。
 */
@Slf4j
@Component
public class TraceQueryCounter {

    private final TraceCodeMapper traceCodeMapper;
    private final TraceProperties traceProperties;

    /** 待落库的增量：traceCodeId -> 累计次数 */
    private final ConcurrentHashMap<Long, AtomicLong> pendingDeltas = new ConcurrentHashMap<>();

    /** 最近一次查询时间：traceCodeId -> 时间 */
    private final ConcurrentHashMap<Long, LocalDateTime> lastQueryTimes = new ConcurrentHashMap<>();

    public TraceQueryCounter(TraceCodeMapper traceCodeMapper, TraceProperties traceProperties) {
        this.traceCodeMapper = traceCodeMapper;
        this.traceProperties = traceProperties;
    }

    /**
     * 记录一次查询（读路径调用，无阻塞、无数据库访问）。
     */
    public void record(Long traceCodeId) {
        if (traceCodeId == null) {
            return;
        }
        pendingDeltas.computeIfAbsent(traceCodeId, k -> new AtomicLong())
                .incrementAndGet();
        lastQueryTimes.put(traceCodeId, LocalDateTime.now());
    }

    /**
     * 取"包含未落库增量"的实时计数，保证接口返回值对用户是即时准确的。
     */
    public long currentCount(Long traceCodeId, Integer databaseCount) {
        long base = databaseCount == null ? 0L : databaseCount;
        if (traceCodeId == null) {
            return base;
        }
        AtomicLong delta = pendingDeltas.get(traceCodeId);
        return base + (delta == null ? 0L : delta.get());
    }

    /**
     * 定时批量落库。
     *
     * <p>先"取走"再写入：用 remove 把条目从 map 摘除，因此刷新期间新到的自增
     * 会落到新的条目上，不会被本次写入覆盖丢失。
     */
    @Scheduled(fixedDelayString = "${fsts.trace.query-count-flush-interval-ms:5000}")
    public void flush() {
        if (pendingDeltas.isEmpty()) {
            return;
        }
        // 单次落库条目数设上限：避免积压时一个周期内长时间占用数据库连接与写锁，
        // 剩余条目留到下一个周期继续写（每个周期 5 秒，积压会被逐步消化）。
        int limit = Math.max(traceProperties.getQueryCountFlushBatch(), 1);
        Map<Long, Long> snapshot = new HashMap<>(Math.min(limit, 512) * 2);
        for (Map.Entry<Long, AtomicLong> entry : pendingDeltas.entrySet()) {
            if (snapshot.size() >= limit) {
                break;
            }
            Long id = entry.getKey();
            AtomicLong delta = pendingDeltas.remove(id);
            if (delta == null) {
                continue;
            }
            long value = delta.get();
            if (value > 0) {
                snapshot.put(id, value);
            }
        }
        if (snapshot.isEmpty()) {
            return;
        }

        int success = 0;
        for (Map.Entry<Long, Long> entry : snapshot.entrySet()) {
            Long id = entry.getKey();
            try {
                traceCodeMapper.addQueryCount(id, entry.getValue(), lastQueryTimes.get(id));
                lastQueryTimes.remove(id);
                success++;
            } catch (Exception e) {
                // 写失败则把增量并回待写队列，下一个周期重试，保证计数不丢
                pendingDeltas.computeIfAbsent(id, k -> new AtomicLong()).addAndGet(entry.getValue());
                log.warn("查询次数落库失败，将在下个周期重试: traceCodeId={} delta={} err={}",
                        id, entry.getValue(), e.getMessage());
            }
        }
        log.debug("溯源查询次数落库完成: 成功 {}/{}", success, snapshot.size());
    }

    /** 供运维观测：当前积压的标识码数量 */
    public int pendingSize() {
        return pendingDeltas.size();
    }
}
