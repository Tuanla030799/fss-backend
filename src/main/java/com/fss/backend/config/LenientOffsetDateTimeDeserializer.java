package com.fss.backend.config;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

class LenientOffsetDateTimeDeserializer extends JsonDeserializer<OffsetDateTime> {
    private final ZoneId fallbackZone;

    LenientOffsetDateTimeDeserializer(ZoneId fallbackZone) {
        this.fallbackZone = fallbackZone;
    }

    @Override
    public OffsetDateTime deserialize(JsonParser parser, DeserializationContext context) throws IOException {
        String value = parser.getValueAsString();
        if (value == null || value.isBlank()) {
            return null;
        }

        String trimmed = value.trim();
        try {
            return OffsetDateTime.parse(trimmed, DateTimeFormatter.ISO_OFFSET_DATE_TIME)
                    .withOffsetSameInstant(ZoneOffset.UTC);
        } catch (DateTimeParseException ignored) {
            try {
                return LocalDateTime.parse(trimmed, DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                        .atZone(fallbackZone)
                        .toOffsetDateTime()
                        .withOffsetSameInstant(ZoneOffset.UTC);
            } catch (DateTimeParseException ex) {
                return (OffsetDateTime) context.handleWeirdStringValue(
                        OffsetDateTime.class,
                        trimmed,
                        "Expected ISO offset date-time or local date-time"
                );
            }
        }
    }
}
