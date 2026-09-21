package com.fsts.trace.vo;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;
import lombok.extern.jackson.Jacksonized;

/**
 * 行政区划下拉项。
 *
 * <p>省列表返回 {@code regionCode/regionName/shortName/sortNo}；
 * 市列表返回 {@code regionCode/regionName/sortNo}（shortName 为 null，
 * 通过 {@link JsonInclude} 控制是否输出，保证与接口文档示例一致）。
 *
 * <p>{@code @Jacksonized} 的作用见 {@link DictItemVO}：本类同样会被写入缓存并反序列化。
 */
@Data
@Builder
@Jacksonized
@JsonInclude(JsonInclude.Include.NON_NULL)
public class RegionVO {

    private String regionCode;

    private String regionName;

    private String shortName;

    private Integer sortNo;
}
