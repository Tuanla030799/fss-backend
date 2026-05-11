package com.fss.backend.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.OffsetDateTime;
import java.time.ZoneId;

@Configuration
public class JacksonConfig {
    @Bean
    Jackson2ObjectMapperBuilderCustomizer offsetDateTimeDeserializer(
            @Value("${app.time-zone:Asia/Ho_Chi_Minh}") String appTimeZone) {
        return builder -> {
            builder.deserializerByType(OffsetDateTime.class,
                    new LenientOffsetDateTimeDeserializer(ZoneId.of(appTimeZone)));
            builder.serializerByType(OffsetDateTime.class, new UtcOffsetDateTimeSerializer());
        };
    }
}
