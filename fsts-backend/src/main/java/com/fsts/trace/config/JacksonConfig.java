package com.fsts.trace.config;

import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateDeserializer;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateTimeDeserializer;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalTimeDeserializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateSerializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalTimeSerializer;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/**
 * Jackson 配置：统一日期时间格式（yyyy-MM-dd HH:mm:ss / yyyy-MM-dd）。
 *
 * <p>为什么不做"Long 全局序列化为字符串"：那会把分页 {@code total/size/current/pages}
 * 与统计 {@code count} 一并变成字符串，破坏接口文档 2.5、7.x 的响应契约。
 * 因此仅对 ID 字段显式标注 {@code @JsonSerialize(using = ToStringSerializer.class)}，
 * 做到"该转的转、不该转的不动"。
 */
@Configuration
public class JacksonConfig {

    public static final String DATE_TIME_PATTERN = "yyyy-MM-dd HH:mm:ss";
    public static final String DATE_PATTERN = "yyyy-MM-dd";
    public static final String TIME_PATTERN = "HH:mm:ss";

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer jacksonCustomizer() {
        DateTimeFormatter dateTime = DateTimeFormatter.ofPattern(DATE_TIME_PATTERN);
        DateTimeFormatter date = DateTimeFormatter.ofPattern(DATE_PATTERN);
        DateTimeFormatter time = DateTimeFormatter.ofPattern(TIME_PATTERN);

        return builder -> builder
                .serializerByType(LocalDateTime.class, new LocalDateTimeSerializer(dateTime))
                .serializerByType(LocalDate.class, new LocalDateSerializer(date))
                .serializerByType(LocalTime.class, new LocalTimeSerializer(time))
                .deserializerByType(LocalDateTime.class, new LocalDateTimeDeserializer(dateTime))
                .deserializerByType(LocalDate.class, new LocalDateDeserializer(date))
                .deserializerByType(LocalTime.class, new LocalTimeDeserializer(time));
    }
}
