package com.fss.backend.catalog.brand;

import com.fss.backend.file.FileAssetRepository;
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
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.fss.backend.shared.mongo.MongoValues.containsIgnoreCase;
import static com.fss.backend.shared.mongo.MongoValues.hasText;
import static com.fss.backend.shared.mongo.MongoValues.live;

@Repository
public class BrandRepository {
    private final MongoTemplate mongo;
    private final FileAssetRepository files;

    public BrandRepository(MongoTemplate mongo, FileAssetRepository files) {
        this.mongo = mongo;
        this.files = files;
    }

    /** Returns brands with {@code imageUrl}/{@code sizeGuideImageUrl} holding the stored file path (service builds URLs). */
    public List<Brand> listBrands(String status, String keyword, int limit, int offset) {
        Query query = Query.query(filter(status, keyword))
                .with(Sort.by(Sort.Order.asc("sortOrder"), Sort.Order.desc("createdAt"), Sort.Order.desc("_id")))
                .skip(offset).limit(limit);
        return toBrands(mongo.find(query, BrandDocument.class));
    }

    public long countBrands(String status, String keyword) {
        return mongo.count(Query.query(filter(status, keyword)), BrandDocument.class);
    }

    public Brand findBrandById(UUID id) {
        return first(mongo.findOne(Query.query(live().and("_id").is(id)), BrandDocument.class));
    }

    public Brand findBrandBySlug(String slug) {
        return first(mongo.findOne(Query.query(live().and("slug").is(slug)), BrandDocument.class));
    }

    /** Any brand (including soft-deleted) keyed by id, for joins. */
    public Map<UUID, BrandDocument> findAllByIds(Collection<UUID> ids) {
        if (ids.isEmpty()) return Map.of();
        return mongo.find(Query.query(Criteria.where("_id").in(ids)), BrandDocument.class).stream()
                .collect(Collectors.toMap(BrandDocument::id, Function.identity()));
    }

    public List<UUID> findActiveIds() {
        return mongo.findDistinct(Query.query(live().and("status").is("ACTIVE")), "_id", BrandDocument.class, UUID.class);
    }

    /** Ids of every brand (including soft-deleted) with this slug; mirrors the old LEFT JOIN on brands.slug. */
    public List<UUID> findIdsBySlug(String slug) {
        return mongo.findDistinct(Query.query(Criteria.where("slug").is(slug)), "_id", BrandDocument.class, UUID.class);
    }

    public boolean slugExists(String slug, UUID excludeId) {
        Criteria criteria = live().and("slug").is(slug);
        if (excludeId != null) criteria = criteria.and("_id").ne(excludeId);
        return mongo.exists(Query.query(criteria), BrandDocument.class);
    }

    public void insertBrand(UUID id, String name, String slug, String description, UUID fileId, UUID fileSizeId,
                            String status, Integer sortOrder, UUID adminId) {
        Instant now = Instant.now();
        mongo.insert(new BrandDocument(id, name, slug, description, fileId, fileSizeId, status, sortOrder, adminId, adminId,
                now, now, false, null));
    }

    public void updateBrand(UUID id, String name, String slug, String description, UUID fileId, UUID fileSizeId,
                            String status, Integer sortOrder, UUID adminId) {
        mongo.updateFirst(Query.query(live().and("_id").is(id)), new Update().set("name", name).set("slug", slug)
                .set("description", description).set("fileId", fileId).set("fileSizeId", fileSizeId).set("status", status)
                .set("sortOrder", sortOrder).set("updatedBy", adminId).set("updatedAt", Instant.now()), BrandDocument.class);
    }

    public void softDeleteBrand(UUID id, UUID adminId) {
        Instant now = Instant.now();
        mongo.updateFirst(Query.query(live().and("_id").is(id)), new Update().set("deleted", true).set("deletedAt", now)
                .set("updatedBy", adminId).set("updatedAt", now), BrandDocument.class);
    }

    private Brand first(BrandDocument doc) {
        return doc == null ? null : toBrands(List.of(doc)).get(0);
    }

    private List<Brand> toBrands(List<BrandDocument> docs) {
        Map<UUID, String> paths = files.findPathsByIds(docs.stream()
                .flatMap(doc -> Stream.of(doc.fileId(), doc.fileSizeId())).filter(Objects::nonNull).collect(Collectors.toSet()));
        return docs.stream().map(doc -> new Brand(doc.id(), doc.name(), doc.slug(), doc.description(),
                doc.fileId(), doc.fileId() == null ? null : paths.get(doc.fileId()),
                doc.fileSizeId(), doc.fileSizeId() == null ? null : paths.get(doc.fileSizeId()),
                doc.status(), doc.sortOrder(), MongoValues.odt(doc.createdAt()))).toList();
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
