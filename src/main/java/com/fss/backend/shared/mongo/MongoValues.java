package com.fss.backend.shared.mongo;

import com.fss.backend.common.ApiException;
import com.fss.backend.common.ErrorCode;
import org.bson.types.Decimal128;
import org.springframework.data.mongodb.core.query.Criteria;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Locale;
import java.util.regex.Pattern;

/** Small conversions shared by the Mongo repositories. */
public final class MongoValues {
    /** PostgreSQL schema used NUMERIC(12,2): at most 10 integer digits and 2 decimals. */
    private static final BigDecimal MAX_MONEY = new BigDecimal("9999999999.99");

    private MongoValues() {
    }

    public static Criteria live() {
        return Criteria.where("deleted").is(false);
    }

    public static OffsetDateTime odt(Instant value) {
        return value == null ? null : value.atOffset(ZoneOffset.UTC);
    }

    public static Instant instant(OffsetDateTime value) {
        return value == null ? null : value.toInstant();
    }

    /** Case-insensitive "contains" match, equivalent to the old {@code ILIKE '%keyword%'}. */
    public static Pattern containsIgnoreCase(String keyword) {
        return Pattern.compile(Pattern.quote(keyword), Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    }

    public static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    public static String lower(String value) {
        return value == null ? null : value.trim().toLowerCase(Locale.ROOT);
    }

    /** Money is kept exact with two decimals, like the previous NUMERIC(12,2) columns. */
    public static BigDecimal money(BigDecimal value) {
        if (value == null) {
            return null;
        }
        BigDecimal scaled = value.setScale(2, RoundingMode.HALF_UP);
        if (scaled.abs().compareTo(MAX_MONEY) > 0) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "Amount is too large");
        }
        return scaled;
    }

    public static Decimal128 decimal(BigDecimal value) {
        return value == null ? null : new Decimal128(value);
    }

    public static BigDecimal bigDecimal(Object value) {
        if (value == null) return null;
        if (value instanceof Decimal128 decimal) return decimal.bigDecimalValue();
        if (value instanceof BigDecimal decimal) return decimal;
        if (value instanceof Number number) return new BigDecimal(number.toString());
        return new BigDecimal(value.toString());
    }
}
