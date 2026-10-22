package com.fss.backend.content.collection;

import com.fss.backend.catalog.product.ProductDocument;
import com.fss.backend.catalog.product.ProductRepository;
import com.fss.backend.catalog.product.ProductSummary;
import com.fss.backend.file.FileAssetRepository;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static com.fss.backend.shared.mongo.MongoValues.containsIgnoreCase;
import static com.fss.backend.shared.mongo.MongoValues.hasText;
import static com.fss.backend.shared.mongo.MongoValues.live;
import static com.fss.backend.shared.mongo.MongoValues.odt;

@Repository
public class ProductCollectionRepository {
    private final MongoTemplate mongo;
    private final FileAssetRepository files;
    private final ProductRepository products;

    public ProductCollectionRepository(MongoTemplate mongo, FileAssetRepository files, ProductRepository products) {
        this.mongo = mongo;
        this.files = files;
        this.products = products;
    }

    public List<ProductCollection> listCollections(boolean publicOnly, String status, String keyword, int limit, int offset) {
        Query query = Query.query(filter(publicOnly, status, keyword))
                .with(Sort.by(Sort.Order.asc("sortOrder"), Sort.Order.desc("createdAt"), Sort.Order.desc("_id")))
                .skip(offset).limit(limit);
        return toCollections(mongo.find(query, CollectionDocument.class));
    }

    public long countCollections(boolean publicOnly, String status, String keyword) {
        return mongo.count(Query.query(filter(publicOnly, status, keyword)), CollectionDocument.class);
    }

    public ProductCollection findCollectionById(UUID id) {
        CollectionDocument doc = mongo.findOne(Query.query(live().and("_id").is(id)), CollectionDocument.class);
        return doc == null ? null : toCollections(List.of(doc)).get(0);
    }

    public ProductCollection findCollectionBySlug(String slug, boolean publicOnly) {
        Criteria criteria = live().and("slug").is(slug);
        if (publicOnly) criteria = criteria.and("status").is("ACTIVE");
        CollectionDocument doc = mongo.findOne(Query.query(criteria), CollectionDocument.class);
        return doc == null ? null : toCollections(List.of(doc)).get(0);
    }

    public boolean slugExists(String slug, UUID excludeId) {
        Criteria criteria = live().and("slug").is(slug);
        if (excludeId != null) criteria = criteria.and("_id").ne(excludeId);
        return mongo.exists(Query.query(criteria), CollectionDocument.class);
    }

    public void insertCollection(UUID id, String name, String slug, String excerpt, String descriptionHtml,
                                 List<String> htmlFilePaths, UUID fileId, String status, Integer sortOrder,
                                 List<CollectionDocument.Member> members, UUID adminId) {
        Instant now = Instant.now();
        mongo.insert(new CollectionDocument(id, name, slug, excerpt, descriptionHtml, htmlFilePaths, fileId, status, sortOrder,
                members, adminId, adminId, now, now, false, null));
    }

    public void updateCollection(UUID id, String name, String slug, String excerpt, String descriptionHtml,
                                 List<String> htmlFilePaths, UUID fileId, String status, Integer sortOrder,
                                 List<CollectionDocument.Member> members, UUID adminId) {
        mongo.updateFirst(Query.query(live().and("_id").is(id)), new Update().set("name", name).set("slug", slug)
                .set("excerpt", excerpt).set("descriptionHtml", descriptionHtml).set("htmlFilePaths", htmlFilePaths)
                .set("fileId", fileId).set("status", status).set("sortOrder", sortOrder).set("products", members)
                .set("updatedBy", adminId).set("updatedAt", Instant.now()), CollectionDocument.class);
    }

    public void softDeleteCollection(UUID id, UUID adminId) {
        Instant now = Instant.now();
        mongo.updateFirst(Query.query(live().and("_id").is(id)), new Update().set("deleted", true).set("deletedAt", now)
                .set("updatedBy", adminId).set("updatedAt", now), CollectionDocument.class);
    }

    /**
     * Products of a collection, ordered by membership sortOrder then product creation date (newest first), like the
     * previous {@code ORDER BY cp.sort_order, p.created_at DESC}.
     */
    public List<ProductSummary> listCollectionProducts(UUID collectionId, boolean publicOnly, int limit, int offset) {
        List<ProductDocument> ordered = orderedProducts(collectionId, publicOnly);
        int from = Math.min(offset, ordered.size());
        int to = Math.min(from + limit, ordered.size());
        return products.summaries(ordered.subList(from, to), publicOnly);
    }

    public long countCollectionProducts(UUID collectionId, boolean publicOnly) {
        return orderedProducts(collectionId, publicOnly).size();
    }

    private List<ProductDocument> orderedProducts(UUID collectionId, boolean publicOnly) {
        Query query = Query.query(Criteria.where("_id").is(collectionId));
        query.fields().include("products").include("deleted");
        CollectionDocument doc = mongo.findOne(query, CollectionDocument.class);
        if (doc == null || doc.products() == null || doc.products().isEmpty()) return List.of();
        Map<UUID, Integer> sortOrders = doc.products().stream().collect(Collectors.toMap(CollectionDocument.Member::productId,
                member -> member.sortOrder() == null ? 0 : member.sortOrder(), (a, b) -> b, LinkedHashMap::new));
        return products.findLiveByIds(sortOrders.keySet(), publicOnly).stream()
                .sorted(Comparator.comparing((ProductDocument product) -> sortOrders.get(product.id()))
                        .thenComparing(ProductDocument::createdAt, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(ProductDocument::id, Comparator.reverseOrder()))
                .toList();
    }

    private Criteria filter(boolean publicOnly, String status, String keyword) {
        Criteria criteria = live();
        if (publicOnly) criteria = criteria.and("status").is("ACTIVE");
        if (hasText(status)) {
            criteria = new Criteria().andOperator(criteria, Criteria.where("status").is(status));
        }
        if (hasText(keyword)) {
            Pattern pattern = containsIgnoreCase(keyword);
            criteria = new Criteria().andOperator(criteria, new Criteria().orOperator(Criteria.where("name").regex(pattern),
                    Criteria.where("excerpt").regex(pattern), Criteria.where("descriptionHtml").regex(pattern)));
        }
        return criteria;
    }

    private List<ProductCollection> toCollections(List<CollectionDocument> docs) {
        Map<UUID, String> paths = files.findPathsByIds(docs.stream().map(CollectionDocument::fileId).filter(Objects::nonNull).toList());
        return docs.stream().map(doc -> new ProductCollection(doc.id(), doc.name(), doc.slug(), doc.excerpt(),
                doc.descriptionHtml(), doc.fileId(), doc.fileId() == null ? null : paths.get(doc.fileId()), doc.status(),
                doc.sortOrder(), doc.products() == null ? 0 : doc.products().size(), odt(doc.createdAt()),
                odt(doc.updatedAt()))).toList();
    }
}
