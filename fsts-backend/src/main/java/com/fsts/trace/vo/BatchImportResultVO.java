package com.fsts.trace.vo;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * 批量导入结果（接口 10.12）。
 *
 * <p>逐行返回结果而不是"整批成功/失败"：导入的常见场景是几十行里错两三行，
 * 整批回滚会让用户反复试错；这里成功的行已入库，失败的行带上行号与原因，
 * 用户改完再导入时会被批号唯一性校验挡住重复行，不会产生重复数据。
 */
@Data
@Builder
public class BatchImportResultVO {

    private int total;

    private int successCount;

    private int failureCount;

    private List<Item> items;

    /** 单行导入结果 */
    @Data
    @Builder
    public static class Item {

        private Integer rowNo;

        private String batchNo;

        private boolean success;

        /** 失败原因（成功时为 null） */
        private String message;

        /** 成功时返回新建批号的主键，便于前端跳转查看 */
        private String batchId;
    }
}
