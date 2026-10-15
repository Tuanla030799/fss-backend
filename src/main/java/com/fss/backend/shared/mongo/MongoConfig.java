package com.fss.backend.shared.mongo;

import org.bson.types.Decimal128;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.convert.ReadingConverter;
import org.springframework.data.convert.WritingConverter;
import org.springframework.data.mongodb.MongoDatabaseFactory;
import org.springframework.data.mongodb.MongoTransactionManager;
import org.springframework.data.mongodb.core.convert.MongoCustomConversions;

import java.math.BigDecimal;
import java.util.List;

/**
 * MongoDB wiring shared by every module.
 *
 * <ul>
 *   <li>BigDecimal is stored as Decimal128 (Spring Data's default is String, which breaks range queries
 *       and loses numeric semantics).</li>
 *   <li>A {@link MongoTransactionManager} makes {@code @Transactional} real. MongoDB transactions require a
 *       replica set or sharded cluster (MongoDB Atlas, or the single-node replica set in docker-compose).</li>
 * </ul>
 */
@Configuration
public class MongoConfig {
    @Bean
    MongoCustomConversions mongoCustomConversions() {
        return new MongoCustomConversions(List.of(new BigDecimalToDecimal128(), new Decimal128ToBigDecimal()));
    }

    @Bean
    MongoTransactionManager transactionManager(MongoDatabaseFactory databaseFactory) {
        return new MongoTransactionManager(databaseFactory);
    }

    @WritingConverter
    static class BigDecimalToDecimal128 implements Converter<BigDecimal, Decimal128> {
        @Override
        public Decimal128 convert(BigDecimal source) {
            return new Decimal128(source);
        }
    }

    @ReadingConverter
    static class Decimal128ToBigDecimal implements Converter<Decimal128, BigDecimal> {
        @Override
        public BigDecimal convert(Decimal128 source) {
            return source.bigDecimalValue();
        }
    }
}
