package com.fss.backend.promotion.coupon;

import org.bson.Document;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.BasicQuery;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

import static com.fss.backend.shared.mongo.MongoValues.containsIgnoreCase;
import static com.fss.backend.shared.mongo.MongoValues.hasText;
import static com.fss.backend.shared.mongo.MongoValues.instant;
import static com.fss.backend.shared.mongo.MongoValues.live;
import static com.fss.backend.shared.mongo.MongoValues.money;
import static com.fss.backend.shared.mongo.MongoValues.odt;

@Repository
public class CouponRepository {
    private final MongoTemplate mongo;

    public CouponRepository(MongoTemplate mongo) {
        this.mongo = mongo;
    }

    public Coupon findCouponByCode(String code) {
        if (code == null) return null;
        return toCoupon(mongo.findOne(Query.query(live().and("code").is(upper(code))), CouponDocument.class));
    }

    public Coupon findCouponById(UUID id) {
        return toCoupon(mongo.findOne(Query.query(live().and("_id").is(id)), CouponDocument.class));
    }

    public boolean codeExists(String code, UUID excludeId) {
        Criteria criteria = live().and("code").is(upper(code));
        if (excludeId != null) criteria = criteria.and("_id").ne(excludeId);
        return mongo.exists(Query.query(criteria), CouponDocument.class);
    }

    public List<Coupon> listCoupons(String status, String keyword, int limit, int offset) {
        Query query = Query.query(filter(status, keyword)).with(Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("_id")))
                .skip(offset).limit(limit);
        return mongo.find(query, CouponDocument.class).stream().map(this::toCoupon).toList();
    }

    public long countCoupons(String status, String keyword) {
        return mongo.count(Query.query(filter(status, keyword)), CouponDocument.class);
    }

    public void insertCoupon(UUID id, String code, String name, String discountType, BigDecimal discountValue,
                             BigDecimal maxDiscount, BigDecimal minOrderAmount, Integer usageLimit, OffsetDateTime startsAt,
                             OffsetDateTime endsAt, String status, UUID adminId) {
        Instant now = Instant.now();
        mongo.insert(new CouponDocument(id, upper(code), name, discountType, money(discountValue), money(maxDiscount),
                money(minOrderAmount), usageLimit, 0, instant(startsAt), instant(endsAt), status, adminId, adminId,
                now, now, false, null));
    }

    public void updateCoupon(UUID id, String code, String name, String discountType, BigDecimal discountValue,
                             BigDecimal maxDiscount, BigDecimal minOrderAmount, Integer usageLimit, OffsetDateTime startsAt,
                             OffsetDateTime endsAt, String status, UUID adminId) {
        mongo.updateFirst(Query.query(live().and("_id").is(id)), new Update().set("code", upper(code)).set("name", name)
                .set("discountType", discountType).set("discountValue", money(discountValue))
                .set("maxDiscount", money(maxDiscount)).set("minOrderAmount", money(minOrderAmount))
                .set("usageLimit", usageLimit).set("startsAt", instant(startsAt)).set("endsAt", instant(endsAt))
                .set("status", status).set("updatedBy", adminId).set("updatedAt", Instant.now()), CouponDocument.class);
    }

    /**
     * Atomically consumes one use. Matches only while the coupon is live, ACTIVE and below its usage limit, so
     * concurrent orders can never push {@code usedCount} past {@code usageLimit}. Returns false when nothing matched.
     */
    public boolean tryConsumeUsage(UUID id) {
        Document filter = new Document("_id", id).append("deleted", false).append("status", "ACTIVE")
                .append("$or", List.of(
                        new Document("usageLimit", null),
                        new Document("$expr", new Document("$lt", List.of("$usedCount", "$usageLimit")))));
        return mongo.updateFirst(new BasicQuery(filter), new Update().inc("usedCount", 1).set("updatedAt", Instant.now()),
                CouponDocument.class).getModifiedCount() == 1;
    }

    public void softDeleteCoupon(UUID id, UUID adminId) {
        Instant now = Instant.now();
        mongo.updateFirst(Query.query(live().and("_id").is(id)), new Update().set("deleted", true).set("deletedAt", now)
                .set("updatedBy", adminId).set("updatedAt", now), CouponDocument.class);
    }

    private Criteria filter(String status, String keyword) {
        Criteria criteria = live();
        if (hasText(status)) criteria = criteria.and("status").is(status);
        if (hasText(keyword)) {
            Pattern pattern = containsIgnoreCase(keyword);
            criteria = criteria.orOperator(Criteria.where("code").regex(pattern), Criteria.where("name").regex(pattern));
        }
        return criteria;
    }

    private static String upper(String code) {
        return code == null ? null : code.toUpperCase(Locale.ROOT);
    }

    private Coupon toCoupon(CouponDocument doc) {
        return doc == null ? null : new Coupon(doc.id(), doc.code(), doc.name(), doc.discountType(), doc.discountValue(),
                doc.maxDiscount(), doc.minOrderAmount(), doc.usageLimit(), doc.usedCount() == null ? 0 : doc.usedCount(),
                odt(doc.startsAt()), odt(doc.endsAt()), doc.status(), odt(doc.createdAt()));
    }
}
