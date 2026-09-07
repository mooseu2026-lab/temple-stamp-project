package com.templestamp.global.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.TimeZone;

/**
 * 시간은 Asia/Seoul 기준 ISO-8601 문자열로 나간다.
 * <p>
 * serializationInclusion 을 건드리지 않는 것이 중요하다. 규약 §8 — null 필드도 키를 내려준다.
 * NON_NULL 로 바꾸면 성공 응답에서 "error" 키가 통째로 사라져, 클라이언트가 스키마를
 * 예측할 수 없게 된다.
 * <p>
 * 모르는 필드가 들어오면 조용히 버리지 않고 400 으로 떨어뜨린다. 오타 난 필드가 무시된 채
 * 기본값으로 저장되는 사고를 막기 위해서다. (외부 API 응답은 @JsonIgnoreProperties 로 개별 예외)
 */
@Configuration
public class JacksonConfig {

    public static final String TIME_ZONE = "Asia/Seoul";

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer objectMapperCustomizer() {
        return builder -> builder
                .modules(new JavaTimeModule())
                .timeZone(TimeZone.getTimeZone(TIME_ZONE))
                .featuresToDisable(
                        SerializationFeature.WRITE_DATES_AS_TIMESTAMPS,
                        SerializationFeature.WRITE_DURATIONS_AS_TIMESTAMPS)
                .featuresToEnable(
                        DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,
                        DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES);
    }
}
