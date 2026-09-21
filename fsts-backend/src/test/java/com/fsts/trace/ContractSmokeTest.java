package com.fsts.trace;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fsts.trace.common.ProvinceAbbr;
import com.fsts.trace.common.Result;
import com.fsts.trace.common.LoginUser;
import com.fsts.trace.common.util.JsonUtils;
import com.fsts.trace.config.JacksonConfig;
import com.fsts.trace.config.props.JwtProperties;
import com.fsts.trace.support.security.JwtUtil;
import com.fsts.trace.vo.BatchListItemVO;
import com.fsts.trace.vo.DictItemVO;
import com.fsts.trace.vo.OverviewVO;
import com.fsts.trace.vo.PageVO;
import com.fsts.trace.vo.RegionVO;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 契约冒烟测试：不依赖数据库与 Redis，只校验最容易出错、且一旦出错就会破坏
 * 前后端契约的几个点。
 */
class ContractSmokeTest {

    /** 建表脚本中 sys_admin 的初始密码密文，必须能被 BCrypt 校验为 123456 */
    private static final String SEED_ADMIN_HASH = "$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVKIUi";

    @Test
    void seedAdminPasswordShouldMatchDefault() {
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        assertTrue(encoder.matches("123456", SEED_ADMIN_HASH),
                "建表脚本中的管理员密码密文无法用 123456 校验通过，登录会必然失败");
    }

    @Test
    void idFieldsShouldSerializeAsStringWhileCountsStayNumeric() throws Exception {
        ObjectMapper mapper = buildMapper();

        BatchListItemVO item = BatchListItemVO.builder()
                .id(1857392012345678123L)
                .batchNo("YY20260901001")
                .batchStatus(3)
                .batchStatusName("已确认")
                .handoverTemp(new java.math.BigDecimal("-19.50"))
                .coldChainOk(true)
                .createTime(LocalDateTime.of(2026, 9, 1, 8, 30, 0))
                .build();

        PageVO<BatchListItemVO> page = PageVO.of(List.of(item), 36L, 10L, 1L);
        String json = mapper.writeValueAsString(Result.ok(page));

        // 主键必须为字符串，避免 JS 精度丢失
        assertTrue(json.contains("\"id\":\"1857392012345678123\""), "主键未序列化为字符串: " + json);
        // 分页字段必须保持数值类型（接口文档 2.5）
        assertTrue(json.contains("\"total\":36"), "total 不应被序列化为字符串: " + json);
        assertTrue(json.contains("\"current\":1"), "current 不应被序列化为字符串: " + json);
    }

    @Test
    void overviewCountsShouldStayNumeric() throws Exception {
        ObjectMapper mapper = buildMapper();
        OverviewVO overview = OverviewVO.builder()
                .totalCount(16L).fishingCount(5L).processingCount(3L)
                .wholesaleCount(5L).retailCount(3L)
                .provinceCount(8L).todayRegisterCount(0L)
                .updateTime(LocalDateTime.of(2026, 9, 21, 9, 20, 0))
                .build();
        String json = mapper.writeValueAsString(overview);
        assertTrue(json.contains("\"totalCount\":16"), "统计计数应保持数值类型: " + json);
    }

    /**
     * 构造与 Web 层一致的 ObjectMapper：
     * Jackson2ObjectMapperBuilder 会自动注册 JavaTimeModule，
     * 再叠加项目自定义的日期格式，等价于 Spring Boot 启动时的行为。
     */
    private ObjectMapper buildMapper() {
        Jackson2ObjectMapperBuilder builder = Jackson2ObjectMapperBuilder.json();
        new JacksonConfig().jacksonCustomizer().customize(builder);
        return builder.build();
    }

    @Test
    void provinceAbbrShouldFollowTraceCodeRule() {
        assertEquals("SH", ProvinceAbbr.of("310000"));
        assertEquals("SD", ProvinceAbbr.of("370000"));
        assertEquals("GD", ProvinceAbbr.of("440000"));
        // 未知省份使用兜底编码，保证溯源码始终可生成
        assertEquals("XX", ProvinceAbbr.of("999999"));
    }

    /**
     * 缓存往返回归测试。
     *
     * <p>背景：{@code CacheService} 以 JSON 存取数据，因此凡是被写入缓存的 VO
     * 都必须能被反序列化回来。Lombok 的 {@code @Builder} 会让 {@code @Data}
     * 不再生成无参构造器，Jackson 随即反序列化失败——这个缺陷只在"第二次读缓存"时暴露，
     * 编译期与首次请求都发现不了（首次是未命中，直接返回数据库查询结果）。
     *
     * <p>本测试用与缓存完全相同的路径做一次往返，确保该类问题不会再次出现。
     */
    @Test
    void cachedValueObjectsShouldSurviveJsonRoundTrip() {
        List<DictItemVO> dictItems = List.of(
                DictItemVO.builder().itemCode("1").itemValue("捕捞与养殖企业").sortNo(1).build(),
                DictItemVO.builder().itemCode("4").itemValue("零售商").sortNo(4).build());
        List<DictItemVO> dictRoundTrip = JsonUtils.parseList(JsonUtils.toJson(dictItems), DictItemVO.class);
        assertEquals(2, dictRoundTrip.size(), "字典项缓存往返后条数不一致");
        assertEquals("捕捞与养殖企业", dictRoundTrip.get(0).getItemValue());
        assertEquals(4, dictRoundTrip.get(1).getSortNo());

        List<RegionVO> provinces = List.of(
                RegionVO.builder().regionCode("310000").regionName("上海市").shortName("上海").sortNo(9).build());
        List<RegionVO> regionRoundTrip = JsonUtils.parseList(JsonUtils.toJson(provinces), RegionVO.class);
        assertEquals(1, regionRoundTrip.size(), "区划缓存往返后条数不一致");
        assertEquals("上海市", regionRoundTrip.get(0).getRegionName());
        assertEquals("上海", regionRoundTrip.get(0).getShortName());

        assertNotNull(JsonUtils.parse("true", Boolean.class), "企业状态缓存的布尔值往返失败");
    }

    /**
     * 令牌唯一性回归测试。
     *
     * <p>背景：JWT 载荷只有 userId / userType / 过期时间等确定性内容，
     * 若不带随机 jti，则"同一秒内为同一用户签发的两个令牌"会完全相同
     * （签名也相同），一旦其中一个进入黑名单，另一个也会被误杀——
     * 现象就是"退出后立刻重新登录，还是提示登录已失效"。
     *
     * <p>本测试断言：连续签发的令牌两两不同，且解析后能取回各自的 jti。
     */
    @Test
    void tokensIssuedInSameSecondMustBeDistinct() {
        JwtProperties properties = new JwtProperties();
        JwtUtil jwtUtil = new JwtUtil(properties);
        LoginUser user = LoginUser.builder()
                .userId(1L)
                .userType("ENTERPRISE")
                .enterpriseId(1L)
                .enterpriseType(1)
                .enterpriseName("青岛远洋渔业有限公司")
                .loginName("qd_yuanye")
                .build();

        String first = jwtUtil.createToken(user);
        String second = jwtUtil.createToken(user);

        assertNotEquals(first, second, "同一秒内签发的两个令牌不应完全相同");

        String firstId = jwtUtil.parseToken(first).getTokenId();
        String secondId = jwtUtil.parseToken(second).getTokenId();
        assertNotNull(firstId, "令牌缺少 jti 声明");
        assertNotNull(secondId, "令牌缺少 jti 声明");
        assertNotEquals(firstId, secondId, "两个令牌的 jti 不应相同，否则黑名单会误杀新令牌");
    }
}
