package com.fsts.trace.service.validator;

import com.fsts.trace.dto.request.BatchSaveRequest;

/**
 * 产品批号校验策略。
 *
 * <p>为什么用策略模式：新建/更新批号的接口路径统一，但四类企业的字段集合完全不同
 * （捕捞要证明编号与起运温度，加工要速冻温度，批发要三温，零售要陈列温度）。
 * 若在 Service 里用 if/else 堆叠，每加一个环节就要改动既有分支，违反开闭原则；
 * 用策略 + 类型注册表，新增环节只需新增一个实现类。
 */
public interface BatchValidator {

    /**
     * 支持的企业类型（1/2/3/4）。
     */
    int supportType();

    /**
     * 校验必填项与取值范围。
     *
     * @throws com.fsts.trace.common.BusinessException 校验失败时抛出 400
     */
    void validate(BatchSaveRequest request);
}
