package com.fsts.trace.vo;

import com.baomidou.mybatisplus.core.metadata.IPage;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * 统一分页出参，字段与接口文档 2.5 完全一致：{@code records / total / size / current / pages}。
 *
 * <p>为什么不直接返回 MyBatis-Plus 的 {@code IPage}：{@code Page} 还会序列化
 * {@code orders / optimizeCountSql / searchCount / maxLimit / countId} 等内部字段，
 * 既是无效载荷也暴露了实现细节。用本类裁剪后契约更干净、响应体更小。
 *
 * @param <T> 记录类型
 */
@Data
public class PageVO<T> implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 当前页数据列表 */
    private List<T> records = new ArrayList<>();

    /** 满足条件的总记录数 */
    private Long total = 0L;

    /** 每页条数 */
    private Long size = 10L;

    /** 当前页码，从 1 开始 */
    private Long current = 1L;

    /** 总页数 */
    private Long pages = 0L;

    public static <T> PageVO<T> of(IPage<T> page) {
        PageVO<T> vo = new PageVO<>();
        vo.setRecords(page.getRecords());
        vo.setTotal(page.getTotal());
        vo.setSize(page.getSize());
        vo.setCurrent(page.getCurrent());
        vo.setPages(page.getPages());
        return vo;
    }

    /**
     * 分页对象转换：实体分页 -> VO 分页（不额外查询数据库）。
     */
    public static <E, T> PageVO<T> of(IPage<E> page, Function<E, T> mapper) {
        PageVO<T> vo = new PageVO<>();
        List<T> records = new ArrayList<>(page.getRecords().size());
        for (E e : page.getRecords()) {
            records.add(mapper.apply(e));
        }
        vo.setRecords(records);
        vo.setTotal(page.getTotal());
        vo.setSize(page.getSize());
        vo.setCurrent(page.getCurrent());
        vo.setPages(page.getPages());
        return vo;
    }

    /**
     * 基于已有列表构造（用于二次筛选或内存分页）。
     */
    public static <T> PageVO<T> of(List<T> records, long total, long size, long current) {
        PageVO<T> vo = new PageVO<>();
        vo.setRecords(records);
        vo.setTotal(total);
        vo.setSize(size);
        vo.setCurrent(current);
        vo.setPages(size <= 0 ? 0 : (total + size - 1) / size);
        return vo;
    }
}
