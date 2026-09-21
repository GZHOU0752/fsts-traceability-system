package com.fsts.trace.vo;

import lombok.Builder;
import lombok.Data;
import lombok.extern.jackson.Jacksonized;

/**
 * 字典项（接口 5.3）。
 *
 * <p><b>为什么必须加 {@code @Jacksonized}：</b>本类会被写入二级缓存并以 JSON 存取，
 * 而 Lombok 的 {@code @Builder} 会生成全参构造器，从而使 {@code @Data} 不再生成无参构造器，
 * 导致 Jackson 反序列化时报 "no Creators, like default constructor, exist"。
 * {@code @Jacksonized} 让 Lombok 额外生成 {@code @JsonDeserialize(builder = ...)}，
 * 使建造者模式对 Jackson 可见，同时保留不可变风格。
 */
@Data
@Builder
@Jacksonized
public class DictItemVO {

    private String itemCode;

    private String itemValue;

    private Integer sortNo;
}
