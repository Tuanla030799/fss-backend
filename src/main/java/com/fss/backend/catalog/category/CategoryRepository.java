package com.fss.backend.catalog.category;

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
import java.util.stream.Collectors;

import static com.fss.backend.shared.mongo.MongoValues.containsIgnoreCase;
import static com.fss.backend.shared.mongo.MongoValues.hasText;
import static com.fss.backend.shared.mongo.MongoValues.live;

@Repository
public class CategoryRepository {
    private final MongoTemplate mongo;

    public CategoryRepository(MongoTemplate mongo) {
        this.mongo = mongo;
    }

    public List<Category> listCategories(String status, String keyword, int limit, int offset) {
        Query query = Query.query(filter(status, keyword))
                .with(Sort.by(Sort.Order.asc("sortOrder"), Sort.Order.desc("createdAt"), Sort.Order.desc("_id")))
                .skip(offset).limit(limit);
        return mongo.find(query, CategoryDocument.class).stream().map(CategoryDocument::toCategory).toList();
    }

    public long countCategories(String status, String keyword) {
        return mongo.count(Query.query(filter(status, keyword)), CategoryDocument.class);
    }

    public Category findCategoryById(UUID id) {
        CategoryDocument doc = mongo.findOne(Query.query(live().and("_id").is(id)), CategoryDocument.class);
        return doc == null ? null : doc.toCategory();
    }

    public Category findCategoryBySlug(String slug) {
        CategoryDocument doc = mongo.findOne(Query.query(live().and("slug").is(slug)), CategoryDocument.class);
        return doc == null ? null : doc.toCategory();
    }

    /** Any category (including soft-deleted), keyed by id. Used to join category names into product summaries. */
    public Map<UUID, CategoryDocument> findAllByIds(Collection<UUID> ids) {
        if (ids.isEmpty()) return Map.of();
        return mongo.find(Query.query(Criteria.where("_id").in(ids)), CategoryDocument.class).stream()
                .collect(Collectors.toMap(CategoryDocument::id, Function.identity()));
    }

    public List<UUID> findActiveIds() {
        return mongo.findDistinct(Query.query(live().and("status").is("ACTIVE")), "_id", CategoryDocument.class, UUID.class);
    }

    /** Ids of every category (including soft-deleted) with this slug; mirrors the old JOIN on categories.slug. */
    public List<UUID> findIdsBySlug(String slug) {
        return mongo.findDistinct(Query.query(Criteria.where("slug").is(slug)), "_id", CategoryDocument.class, UUID.class);
    }

    public boolean slugExists(String slug, UUID excludeId) {
        Criteria criteria = live().and("slug").is(slug);
        if (excludeId != null) criteria = criteria.and("_id").ne(excludeId);
        return mongo.exists(Query.query(criteria), CategoryDocument.class);
    }

    public void insertCategory(UUID id, UUID parentId, String name, String slug, String description, String status,
                               Integer sortOrder, UUID adminId) {
        Instant now = Instant.now();
        mongo.insert(new CategoryDocument(id, parentId, name, slug, description, status, sortOrder, adminId, adminId,
                now, now, false, null));
    }

    public void updateCategory(UUID id, UUID parentId, String name, String slug, String description, String status,
                               Integer sortOrder, UUID adminId) {
        mongo.updateFirst(Query.query(live().and("_id").is(id)), new Update().set("parentId", parentId).set("name", name)
                .set("slug", slug).set("description", description).set("status", status).set("sortOrder", sortOrder)
                .set("updatedBy", adminId).set("updatedAt", Instant.now()), CategoryDocument.class);
    }

    public void softDeleteCategory(UUID id, UUID adminId) {
        Instant now = Instant.now();
        mongo.updateFirst(Query.query(live().and("_id").is(id)), new Update().set("deleted", true).set("deletedAt", now)
                .set("updatedBy", adminId).set("updatedAt", now), CategoryDocument.class);
    }

    private Criteria filter(String status, String keyword) {
        Criteria criteria = live();
        if (hasText(status)) criteria = criteria.and("status").is(status);
        if (hasText(keyword)) {
            criteria = criteria.orOperator(Criteria.where("name").regex(containsIgnoreCase(keyword)),
                    Criteria.where("slug").regex(containsIgnoreCase(keyword)));
        }
        return criteria;
    }
}
