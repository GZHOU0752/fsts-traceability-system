package com.fsts.trace.common.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fsts.trace.common.BusinessException;
import com.fsts.trace.common.ErrorCode;
import com.fsts.trace.config.JacksonConfig;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

/**
 * JSON 工具：缓存序列化 / 请求指纹计算等内部用途。
 *
 * <p>与 Web 层共用同一套日期格式，保证"缓存读出来的对象"和"接口返回的对象"完全一致。
 */
@Slf4j
public final class JsonUtils {

    private static final ObjectMapper MAPPER = JsonMapper.builder()
            .addModule(new JavaTimeModule())
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();

    static {
        DateTimeFormatter dateTime = DateTimeFormatter.ofPattern(JacksonConfig.DATE_TIME_PATTERN);
        DateTimeFormatter date = DateTimeFormatter.ofPattern(JacksonConfig.DATE_PATTERN);
        DateTimeFormatter time = DateTimeFormatter.ofPattern(JacksonConfig.TIME_PATTERN);
        MAPPER.setDateFormat(new java.text.SimpleDateFormat(JacksonConfig.DATE_TIME_PATTERN));

        var module = new com.fasterxml.jackson.databind.module.SimpleModule();
        module.addSerializer(LocalDateTime.class, new com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer(dateTime));
        module.addSerializer(LocalDate.class, new com.fasterxml.jackson.datatype.jsr310.ser.LocalDateSerializer(date));
        module.addSerializer(LocalTime.class, new com.fasterxml.jackson.datatype.jsr310.ser.LocalTimeSerializer(time));
        module.addDeserializer(LocalDateTime.class, new com.fasterxml.jackson.datatype.jsr310.deser.LocalDateTimeDeserializer(dateTime));
        module.addDeserializer(LocalDate.class, new com.fasterxml.jackson.datatype.jsr310.deser.LocalDateDeserializer(date));
        module.addDeserializer(LocalTime.class, new com.fasterxml.jackson.datatype.jsr310.deser.LocalTimeDeserializer(time));
        MAPPER.registerModule(module);
    }

    private JsonUtils() {
    }

    public static String toJson(Object value) {
        try {
            return MAPPER.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            // 必须记录原始异常：否则"序列化失败"这种笼统提示会让排查无从下手
            log.error("JSON 序列化失败, type={}", value == null ? "null" : value.getClass().getName(), e);
            throw BusinessException.of(ErrorCode.SYSTEM_ERROR, "序列化失败");
        }
    }

    public static <T> T parse(String json, Class<T> type) {
        try {
            return MAPPER.readValue(json, type);
        } catch (JsonProcessingException e) {
            log.error("JSON 反序列化失败, targetType={}, json={}", type.getName(), abbreviate(json), e);
            throw BusinessException.of(ErrorCode.SYSTEM_ERROR, "反序列化失败");
        }
    }

    public static <T> T parse(String json, TypeReference<T> type) {
        try {
            return MAPPER.readValue(json, type);
        } catch (JsonProcessingException e) {
            log.error("JSON 反序列化失败, json={}", abbreviate(json), e);
            throw BusinessException.of(ErrorCode.SYSTEM_ERROR, "反序列化失败");
        }
    }

    /**
     * 解析为 List&lt;T&gt;。
     *
     * <p>必须显式传入元素类型并用 {@link JavaType} 构造：
     * 若写成 {@code new TypeReference<List<T>>(){}}，泛型 T 会被擦除为 Object，
     * 结果元素变成 LinkedHashMap，调用方取值时才抛 ClassCastException。
     */
    public static <T> List<T> parseList(String json, Class<T> elementType) {
        try {
            JavaType listType = MAPPER.getTypeFactory().constructCollectionType(List.class, elementType);
            return MAPPER.readValue(json, listType);
        } catch (JsonProcessingException e) {
            log.error("JSON 反序列化失败, elementType={}, json={}", elementType.getName(), abbreviate(json), e);
            throw BusinessException.of(ErrorCode.SYSTEM_ERROR, "反序列化失败");
        }
    }

    /** 日志中截断超长 JSON，避免把大报文刷进日志 */
    private static String abbreviate(String json) {
        if (json == null) {
            return "null";
        }
        return json.length() <= 512 ? json : json.substring(0, 512) + "...(truncated)";
    }

    /** 计算请求体指纹（用于幂等判定），使用 Jackson 的稳定输出 */
    public static String fingerprint(Object value) {
        return Integer.toHexString(toJson(value).hashCode());
    }
}
