package com.fss.backend.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

class LenientOffsetDateTimeDeserializerTest {
    private final ObjectMapper objectMapper = objectMapper();

    @Test
    void deserializesOffsetDateTimeWithExplicitOffset() throws Exception {
        Event event = objectMapper.readValue("{\"publishedAt\":\"2026-05-11T10:09:00+07:00\"}", Event.class);

        assertThat(event.publishedAt()).isEqualTo(OffsetDateTime.parse("2026-05-11T03:09:00Z"));
    }

    @Test
    void deserializesLocalDateTimeWithConfiguredFallbackZone() throws Exception {
        Event event = objectMapper.readValue("{\"publishedAt\":\"2026-05-11T10:09\"}", Event.class);

        assertThat(event.publishedAt()).isEqualTo(OffsetDateTime.parse("2026-05-11T03:09:00Z"));
    }

    @Test
    void serializesOffsetDateTimeAsUtc() throws Exception {
        String json = objectMapper.writeValueAsString(new Event(OffsetDateTime.parse("2026-05-11T10:09:00+07:00")));

        assertThat(json).isEqualTo("{\"publishedAt\":\"2026-05-11T03:09:00Z\"}");
    }

    private static ObjectMapper objectMapper() {
        SimpleModule module = new SimpleModule();
        module.addDeserializer(OffsetDateTime.class,
                new LenientOffsetDateTimeDeserializer(ZoneId.of("Asia/Ho_Chi_Minh")));
        module.addSerializer(OffsetDateTime.class, new UtcOffsetDateTimeSerializer());
        return new ObjectMapper().registerModule(module);
    }

    private record Event(OffsetDateTime publishedAt) {}
}
