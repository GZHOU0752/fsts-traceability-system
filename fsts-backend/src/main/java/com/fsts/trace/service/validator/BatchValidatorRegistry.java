package com.fsts.trace.service.validator;

import com.fsts.trace.common.BusinessException;
import com.fsts.trace.common.ErrorCode;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 校验策略注册表：按企业类型分发到对应的 {@link BatchValidator}。
 */
@Component
public class BatchValidatorRegistry {

    private final List<BatchValidator> validators;
    private final Map<Integer, BatchValidator> registry = new HashMap<>();

    public BatchValidatorRegistry(List<BatchValidator> validators) {
        this.validators = validators;
    }

    @PostConstruct
    public void init() {
        for (BatchValidator validator : validators) {
            registry.put(validator.supportType(), validator);
        }
    }

    /**
     * 取指定企业类型的校验器。
     */
    public BatchValidator of(Integer enterpriseType) {
        BatchValidator validator = enterpriseType == null ? null : registry.get(enterpriseType);
        if (validator == null) {
            throw BusinessException.of(ErrorCode.PARAM_INVALID, "不支持的企业类型：" + enterpriseType);
        }
        return validator;
    }
}
