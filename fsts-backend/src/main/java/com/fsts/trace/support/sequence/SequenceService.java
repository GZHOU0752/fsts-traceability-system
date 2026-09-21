package com.fsts.trace.support.sequence;

import com.fsts.trace.mapper.SysSequenceMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 原子取号服务。
 *
 * <p>事务传播设为 REQUIRES_NEW：取号必须尽快提交，让行锁立刻释放。
 * 若并入外层业务事务，行锁会一直持有到业务结束，所有并发取号请求将被串行化，
 * 这是典型的"锁放大"陷阱。
 */
@Slf4j
@Service
public class SequenceService {

    private final SysSequenceMapper sequenceMapper;

    public SequenceService(SysSequenceMapper sequenceMapper) {
        this.sequenceMapper = sequenceMapper;
    }

    /**
     * 取下一个序号（从 1 开始）。
     *
     * @param seqKey 序列键，如 enterprise:2026、trace:20260915:SH
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public long next(String seqKey) {
        sequenceMapper.nextValue(seqKey);
        long value = sequenceMapper.currentValue(seqKey);
        if (value <= 0) {
            // 极端异常兜底：理论上不会进入（UPSERT 保证首次插入值为 1）
            log.error("取号结果异常，seqKey={}, value={}", seqKey, value);
            throw new IllegalStateException("序列号生成异常: " + seqKey);
        }
        return value;
    }
}
