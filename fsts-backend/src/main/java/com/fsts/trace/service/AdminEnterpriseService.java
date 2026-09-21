package com.fsts.trace.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fsts.trace.common.BusinessException;
import com.fsts.trace.common.Constants;
import com.fsts.trace.common.ErrorCode;
import com.fsts.trace.dto.query.AdminEnterpriseQuery;
import com.fsts.trace.dto.request.EnterpriseCreateRequest;
import com.fsts.trace.dto.request.EnterpriseUpdateRequest;
import com.fsts.trace.entity.NodeEnterprise;
import com.fsts.trace.mapper.NodeEnterpriseMapper;
import com.fsts.trace.service.support.EnterpriseConverter;
import com.fsts.trace.service.support.EnterpriseValidator;
import com.fsts.trace.support.security.EnterpriseStatusChecker;
import com.fsts.trace.support.sequence.SequenceService;
import com.fsts.trace.vo.CheckAvailableVO;
import com.fsts.trace.vo.EnterpriseCreateResultVO;
import com.fsts.trace.vo.EnterpriseDetailVO;
import com.fsts.trace.vo.EnterpriseListItemVO;
import com.fsts.trace.vo.PageVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.Year;
import java.util.HashMap;
import java.util.Map;

/**
 * 系统管理端 - 节点企业注册信息管理（接口 6.1 ~ 6.6）。
 */
@Slf4j
@Service
public class AdminEnterpriseService {

    /**
     * 唯一性校验的字段白名单。
     *
     * <p>接口 6.6 的 field 允许值仅 loginName / creditCode。
     * 由于该字段最终会拼进 SQL 的列名（无法用 #{} 参数化），
     * 必须做白名单映射 —— 这是防止 SQL 注入的关键一步。
     */
    private static final Map<String, String> CHECK_FIELD_COLUMNS = new HashMap<>();

    static {
        CHECK_FIELD_COLUMNS.put("loginName", "login_name");
        CHECK_FIELD_COLUMNS.put("creditCode", "credit_code");
        CHECK_FIELD_COLUMNS.put("enterpriseName", "enterprise_name");
    }

    private final NodeEnterpriseMapper enterpriseMapper;
    private final EnterpriseConverter converter;
    private final EnterpriseValidator validator;
    private final PasswordEncoder passwordEncoder;
    private final SequenceService sequenceService;
    private final RegionService regionService;
    private final EnterpriseStatusChecker statusChecker;

    public AdminEnterpriseService(NodeEnterpriseMapper enterpriseMapper,
                                  EnterpriseConverter converter,
                                  EnterpriseValidator validator,
                                  PasswordEncoder passwordEncoder,
                                  SequenceService sequenceService,
                                  RegionService regionService,
                                  EnterpriseStatusChecker statusChecker) {
        this.enterpriseMapper = enterpriseMapper;
        this.converter = converter;
        this.validator = validator;
        this.passwordEncoder = passwordEncoder;
        this.sequenceService = sequenceService;
        this.regionService = regionService;
        this.statusChecker = statusChecker;
    }

    /**
     * 分页查询节点企业（接口 6.1）。
     *
     * <p>查询条件全部为空时返回全部企业（逻辑删除记录由 @TableLogic 自动排除）。
     * 模糊匹配用 LIKE 'xxx%' 形式可命中索引前缀，
     * 但企业名称的中缀查询（'%水产%'）无法走索引 —— 这是本项目数据量级下的合理取舍，
     * 若未来企业数达到百万级，应引入 Elasticsearch 或 MySQL 全文索引。
     */
    public PageVO<EnterpriseListItemVO> page(AdminEnterpriseQuery query) {
        Page<NodeEnterprise> page = new Page<>(query.safeCurrent(), query.safeSize());
        IPage<NodeEnterprise> result = enterpriseMapper.selectPage(page, Wrappers.<NodeEnterprise>lambdaQuery()
                .like(StringUtils.hasText(query.getEnterpriseName()), NodeEnterprise::getEnterpriseName, query.getEnterpriseName())
                .like(StringUtils.hasText(query.getCreditCode()), NodeEnterprise::getCreditCode, query.getCreditCode())
                .eq(StringUtils.hasText(query.getProvinceCode()), NodeEnterprise::getProvinceCode, query.getProvinceCode())
                .eq(StringUtils.hasText(query.getCityCode()), NodeEnterprise::getCityCode, query.getCityCode())
                .eq(query.getEnterpriseType() != null, NodeEnterprise::getEnterpriseType, query.getEnterpriseType())
                .eq(query.getStatus() != null, NodeEnterprise::getStatus, query.getStatus())
                .orderByDesc(NodeEnterprise::getRegisterTime)
                .orderByDesc(NodeEnterprise::getId));

        return PageVO.of(result, converter::toListItem);
    }

    /**
     * 节点企业详情（接口 6.2）。
     */
    public EnterpriseDetailVO detail(Long id) {
        return converter.toDetail(requireEnterprise(id));
    }

    /**
     * 新建节点企业（接口 6.3）。
     */
    @Transactional(rollbackFor = Exception.class)
    public EnterpriseCreateResultVO create(EnterpriseCreateRequest request) {
        validator.validateCreate(request);

        String provinceName = resolveProvinceName(request.getProvinceCode());
        String cityName = resolveCityName(request.getProvinceCode(), request.getCityCode());

        // 先查一次给出友好提示；真正的唯一性由数据库唯一索引保证（并发下先查后插必有竞态）
        ensureUnique(request.getLoginName(), request.getCreditCode(), null);

        String initialPassword = StringUtils.hasText(request.getPassword())
                ? request.getPassword()
                : Constants.DEFAULT_PASSWORD;

        // 删除后重新注册同一家企业：复用被逻辑删除的原记录，否则会撞唯一索引
        NodeEnterprise revived = enterpriseMapper.selectDeletedByLoginNameOrCreditCode(
                request.getLoginName(), request.getCreditCode());

        NodeEnterprise entity = revived != null ? revived : new NodeEnterprise();
        applyRequest(entity, request, request.getEnterpriseType(), provinceName, cityName);
        entity.setStatus(request.getStatus() == null ? Constants.STATUS_ENABLED : request.getStatus());
        entity.setPassword(passwordEncoder.encode(initialPassword));
        entity.setLastLoginTime(null);

        if (revived != null) {
            entity.setDeleted(0);
            enterpriseMapper.updateById(entity);
            enterpriseMapper.reviveById(entity.getId());
            statusChecker.evict(entity.getId());
            log.info("复用已被删除的企业记录完成重新注册: id={} code={}", entity.getId(), entity.getEnterpriseCode());
        } else {
            entity.setEnterpriseCode(generateEnterpriseCode());
            entity.setRegisterTime(LocalDateTime.now());
            enterpriseMapper.insert(entity);
        }

        return EnterpriseCreateResultVO.builder()
                .id(entity.getId())
                .enterpriseCode(entity.getEnterpriseCode())
                .loginName(entity.getLoginName())
                .initialPassword(initialPassword)
                .build();
    }

    /**
     * 更新节点企业（接口 6.4）。
     */
    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, EnterpriseUpdateRequest request) {
        NodeEnterprise existing = requireEnterprise(id);

        // 已注册企业变更类型会破坏既有溯源链，明确拒绝
        if (request.getEnterpriseType() != null
                && !request.getEnterpriseType().equals(existing.getEnterpriseType())) {
            throw BusinessException.of(ErrorCode.PARAM_INVALID, "企业类型不允许修改");
        }

        validator.validateUpdate(existing.getEnterpriseType(), request);
        ensureUnique(request.getLoginName(), request.getCreditCode(), id);

        String provinceName = resolveProvinceName(request.getProvinceCode());
        String cityName = resolveCityName(request.getProvinceCode(), request.getCityCode());

        applyRequest(existing, request, existing.getEnterpriseType(), provinceName, cityName);
        enterpriseMapper.updateById(existing);

        // 状态可能被改停用，立即刷新缓存
        statusChecker.evict(id);
    }

    /**
     * 删除节点企业（接口 6.5）：逻辑删除 + 停用账号。
     *
     * <p>逻辑删除而非物理删除的原因：历史批号与溯源链必须保持可追溯，
     * 企业表被物理删除会导致外键失败、历史链断裂。
     */
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        NodeEnterprise existing = requireEnterprise(id);
        NodeEnterprise update = new NodeEnterprise();
        update.setId(existing.getId());
        update.setStatus(Constants.STATUS_DISABLED);
        enterpriseMapper.updateById(update);
        // 逻辑删除（@TableLogic 生效，等价于 UPDATE ... SET deleted = 1）
        enterpriseMapper.deleteById(id);
        statusChecker.evict(id);
        log.info("节点企业已逻辑删除: id={} name={}", id, existing.getEnterpriseName());
    }

    /**
     * 企业名称 / 登录账号 / 营业执照编号唯一性校验（接口 6.6）。
     */
    public CheckAvailableVO checkAvailable(String field, String value, Long excludeId) {
        String column = CHECK_FIELD_COLUMNS.get(field);
        if (column == null) {
            throw BusinessException.of(ErrorCode.PARAM_INVALID, "不支持的校验字段：" + field);
        }
        if (!StringUtils.hasText(value)) {
            throw BusinessException.of(ErrorCode.PARAM_INVALID, "待校验的值不能为空");
        }
        long count = enterpriseMapper.countByColumn(column, value, excludeId);
        return new CheckAvailableVO(count == 0);
    }

    // ------------------------------------------------------------------
    // 内部方法
    // ------------------------------------------------------------------

    private NodeEnterprise requireEnterprise(Long id) {
        NodeEnterprise enterprise = enterpriseMapper.selectById(id);
        if (enterprise == null) {
            throw BusinessException.of(ErrorCode.NOT_FOUND, "节点企业不存在");
        }
        return enterprise;
    }

    /**
     * 唯一性校验：登录账号唯一、统一社会信用代码唯一。
     */
    private void ensureUnique(String loginName, String creditCode, Long excludeId) {
        if (StringUtils.hasText(loginName)
                && enterpriseMapper.countByColumn("login_name", loginName, excludeId) > 0) {
            throw BusinessException.of(ErrorCode.ENTERPRISE_EXISTS, "该登录账号已被注册");
        }
        if (StringUtils.hasText(creditCode)
                && enterpriseMapper.countByColumn("credit_code", creditCode, excludeId) > 0) {
            throw BusinessException.of(ErrorCode.ENTERPRISE_EXISTS, "该统一社会信用代码已注册");
        }
    }

    /**
     * 企业编码生成：FSTS-E-{注册年份}{4 位流水}。
     */
    private String generateEnterpriseCode() {
        String year = String.valueOf(Year.now().getValue());
        long seq = sequenceService.next("enterprise:" + year);
        return Constants.ENTERPRISE_CODE_PREFIX + year + String.format("%04d", seq);
    }

    private String resolveProvinceName(String provinceCode) {
        String name = regionService.provinceName(provinceCode);
        if (name == null) {
            throw BusinessException.of(ErrorCode.PARAM_INVALID, "所在省代码无效");
        }
        return name;
    }

    private String resolveCityName(String provinceCode, String cityCode) {
        String name = regionService.cityName(provinceCode, cityCode);
        if (name == null) {
            throw BusinessException.of(ErrorCode.PARAM_INVALID, "所在市代码无效或不属于所选省份");
        }
        return name;
    }

    /**
     * 把请求体字段写入实体。
     */
    private void applyRequest(NodeEnterprise entity, com.fsts.trace.dto.request.EnterpriseSaveRequest request,
                              Integer enterpriseType, String provinceName, String cityName) {
        entity.setEnterpriseName(request.getEnterpriseName());
        entity.setEnterpriseType(enterpriseType);
        entity.setLoginName(request.getLoginName());
        entity.setCreditCode(request.getCreditCode());
        entity.setLegalPerson(request.getLegalPerson());
        entity.setContactPerson(request.getContactPerson());
        entity.setContactPhone(request.getContactPhone());
        entity.setProvinceCode(request.getProvinceCode());
        entity.setProvinceName(provinceName);
        entity.setCityCode(request.getCityCode());
        entity.setCityName(cityName);
        entity.setAddress(request.getAddress());

        entity.setFisheryLicenseNo(request.getFisheryLicenseNo());
        entity.setAquacultureLicenseNo(request.getAquacultureLicenseNo());
        entity.setFryLicenseNo(request.getFryLicenseNo());
        entity.setFoodProductionLicenseNo(request.getFoodProductionLicenseNo());
        entity.setExportFilingNo(request.getExportFilingNo());
        entity.setFoodBusinessLicenseNo(request.getFoodBusinessLicenseNo());
        entity.setRoadTransportLicenseNo(request.getRoadTransportLicenseNo());
        entity.setColdStorageCapacity(request.getColdStorageCapacity());
        entity.setTransportToolInfo(request.getTransportToolInfo());
        entity.setDisplayEquipmentInfo(request.getDisplayEquipmentInfo());

        // 接口 6.3 业务规则 4：不属于该类型的资质字段忽略且不报错
        validator.clearIrrelevantLicenses(enterpriseType, entity);
    }
}
