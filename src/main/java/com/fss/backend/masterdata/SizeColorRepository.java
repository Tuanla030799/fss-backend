package com.fss.backend.masterdata;

import com.fss.backend.shared.mongo.MongoValues;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static com.fss.backend.shared.mongo.MongoValues.containsIgnoreCase;
import static com.fss.backend.shared.mongo.MongoValues.hasText;
import static com.fss.backend.shared.mongo.MongoValues.live;

@Repository
public class SizeColorRepository {
    private final MongoTemplate mongo;

    public SizeColorRepository(MongoTemplate mongo) {
        this.mongo = mongo;
    }

    public List<SizeOption> listSizes(String status, String keyword, int limit, int offset) {
        Query query = Query.query(filter(status, keyword, "value", "label"))
                .with(Sort.by(Sort.Order.asc("sortOrder"), Sort.Order.asc("value"), Sort.Order.asc("_id")))
                .skip(offset).limit(limit);
        return mongo.find(query, SizeDocument.class).stream().map(SizeDocument::toOption).toList();
    }

    public long countSizes(String status, String keyword) {
        return mongo.count(Query.query(filter(status, keyword, "value", "label")), SizeDocument.class);
    }

    public SizeOption findSizeById(UUID id) {
        SizeDocument doc = mongo.findOne(Query.query(live().and("_id").is(id)), SizeDocument.class);
        return doc == null ? null : doc.toOption();
    }

    public SizeOption findSizeByValue(String value) {
        SizeDocument doc = mongo.findOne(Query.query(live().and("valueKey").is(MongoValues.lower(value))), SizeDocument.class);
        return doc == null ? null : doc.toOption();
    }

    /** Any size (including soft-deleted) keyed by id, for joins into SKUs. */
    public Map<UUID, SizeDocument> findSizesByIds(Collection<UUID> ids) {
        if (ids.isEmpty()) return Map.of();
        return mongo.find(Query.query(Criteria.where("_id").in(ids)), SizeDocument.class).stream()
                .collect(Collectors.toMap(SizeDocument::id, Function.identity()));
    }

    /** Ids of sizes whose value exactly matches one of the filters (old: {@code sz.value IN (...)}). */
    public List<UUID> findSizeIdsByValues(Collection<String> values) {
        return mongo.findDistinct(Query.query(Criteria.where("value").in(values)), "_id", SizeDocument.class, UUID.class);
    }

    public boolean sizeValueExists(String value, UUID excludeId) {
        Criteria criteria = live().and("valueKey").is(MongoValues.lower(value));
        if (excludeId != null) criteria = criteria.and("_id").ne(excludeId);
        return mongo.exists(Query.query(criteria), SizeDocument.class);
    }

    public void insertSize(UUID id, String value, String label, String status, Integer sortOrder, UUID adminId) {
        Instant now = Instant.now();
        mongo.insert(new SizeDocument(id, value, MongoValues.lower(value), label, status, sortOrder, adminId, adminId,
                now, now, false, null));
    }

    public void updateSize(UUID id, String value, String label, String status, Integer sortOrder, UUID adminId) {
        mongo.updateFirst(Query.query(live().and("_id").is(id)), new Update().set("value", value)
                .set("valueKey", MongoValues.lower(value)).set("label", label).set("status", status)
                .set("sortOrder", sortOrder).set("updatedBy", adminId).set("updatedAt", Instant.now()), SizeDocument.class);
    }

    public void softDeleteSize(UUID id, UUID adminId) {
        softDelete(id, adminId, SizeDocument.class);
    }

    public List<ColorOption> listColors(String status, String keyword, int limit, int offset) {
        Query query = Query.query(filter(status, keyword, "name", "colorCode"))
                .with(Sort.by(Sort.Order.asc("sortOrder"), Sort.Order.asc("name"), Sort.Order.asc("_id")))
                .skip(offset).limit(limit);
        return mongo.find(query, ColorDocument.class).stream().map(ColorDocument::toOption).toList();
    }

    public long countColors(String status, String keyword) {
        return mongo.count(Query.query(filter(status, keyword, "name", "colorCode")), ColorDocument.class);
    }

    public ColorOption findColorById(UUID id) {
        ColorDocument doc = mongo.findOne(Query.query(live().and("_id").is(id)), ColorDocument.class);
        return doc == null ? null : doc.toOption();
    }

    public ColorOption findColorByName(String name) {
        ColorDocument doc = mongo.findOne(Query.query(live().and("nameKey").is(MongoValues.lower(name))), ColorDocument.class);
        return doc == null ? null : doc.toOption();
    }

    /** Any color (including soft-deleted) keyed by id, for joins into variants. */
    public Map<UUID, ColorDocument> findColorsByIds(Collection<UUID> ids) {
        if (ids.isEmpty()) return Map.of();
        return mongo.find(Query.query(Criteria.where("_id").in(ids)), ColorDocument.class).stream()
                .collect(Collectors.toMap(ColorDocument::id, Function.identity()));
    }

    /** Ids of colors whose name or code contains any of the filters, case-insensitively. */
    public List<UUID> findColorIdsMatching(Collection<String> terms) {
        List<Criteria> any = terms.stream().flatMap(term -> {
            Pattern pattern = containsIgnoreCase(term);
            return java.util.stream.Stream.of(Criteria.where("name").regex(pattern), Criteria.where("colorCode").regex(pattern));
        }).toList();
        return mongo.findDistinct(Query.query(new Criteria().orOperator(any)), "_id", ColorDocument.class, UUID.class);
    }

    public boolean colorNameExists(String name, UUID excludeId) {
        Criteria criteria = live().and("nameKey").is(MongoValues.lower(name));
        if (excludeId != null) criteria = criteria.and("_id").ne(excludeId);
        return mongo.exists(Query.query(criteria), ColorDocument.class);
    }

    public void insertColor(UUID id, String name, String colorCode, String status, Integer sortOrder, UUID adminId) {
        Instant now = Instant.now();
        mongo.insert(new ColorDocument(id, name, MongoValues.lower(name), colorCode, status, sortOrder, adminId, adminId,
                now, now, false, null));
    }

    public void updateColor(UUID id, String name, String colorCode, String status, Integer sortOrder, UUID adminId) {
        mongo.updateFirst(Query.query(live().and("_id").is(id)), new Update().set("name", name)
                .set("nameKey", MongoValues.lower(name)).set("colorCode", colorCode).set("status", status)
                .set("sortOrder", sortOrder).set("updatedBy", adminId).set("updatedAt", Instant.now()), ColorDocument.class);
    }

    public void softDeleteColor(UUID id, UUID adminId) {
        softDelete(id, adminId, ColorDocument.class);
    }

    private void softDelete(UUID id, UUID adminId, Class<?> type) {
        Instant now = Instant.now();
        mongo.updateFirst(Query.query(live().and("_id").is(id)), new Update().set("deleted", true).set("deletedAt", now)
                .set("updatedBy", adminId).set("updatedAt", now), type);
    }

    private Criteria filter(String status, String keyword, String firstField, String secondField) {
        Criteria criteria = live();
        if (hasText(status)) criteria = criteria.and("status").is(status);
        if (hasText(keyword)) {
            criteria = criteria.orOperator(Criteria.where(firstField).regex(containsIgnoreCase(keyword)),
                    Criteria.where(secondField).regex(containsIgnoreCase(keyword)));
        }
        return criteria;
    }
}
