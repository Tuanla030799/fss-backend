package com.fss.backend.file;

import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * Answers "is this file still used?" across every collection that can point at a file.
 *
 * <p>Direct references are stored as file ids ({@code images.fileId}, {@code imageFileId}, {@code fileId},
 * {@code fileSizeId}, {@code coverFileId}). Images inside rich-text HTML are tracked through the
 * {@code htmlFilePaths} array that products, collections, blog posts, site pages and FAQs maintain on every save. All
 * of these fields are indexed (see {@code MongoIndexInitializer}).
 */
@Component
public class FileUsageIndex {
    static final String PRODUCTS = "products";
    static final String VARIANTS = "product_variants";
    static final String BRANDS = "brands";
    static final String BANNERS = "landing_banners";
    static final String COLLECTIONS = "collections";
    static final String BLOG_POSTS = "blog_posts";
    static final String SITE_PAGES = "site_pages";
    static final String FAQS = "faqs";
    static final List<String> HTML_COLLECTIONS = List.of(PRODUCTS, COLLECTIONS, BLOG_POSTS, SITE_PAGES, FAQS);

    private final MongoTemplate mongo;

    public FileUsageIndex(MongoTemplate mongo) {
        this.mongo = mongo;
    }

    /** References from live (not soft-deleted) records, including live HTML content. */
    public boolean hasActiveReferences(UUID id, String path) {
        if (id != null) {
            if (exists(PRODUCTS, live().and("images.fileId").is(id))) return true;
            if (liveVariantReference(id)) return true;
            if (exists(BRANDS, live().orOperator(Criteria.where("fileId").is(id), Criteria.where("fileSizeId").is(id)))) return true;
            if (exists(BANNERS, live().and("fileId").is(id))) return true;
            if (exists(COLLECTIONS, live().and("fileId").is(id))) return true;
            if (exists(BLOG_POSTS, live().and("coverFileId").is(id))) return true;
        }
        return hasLiveHtmlReference(path);
    }

    /**
     * Guard used right before physically deleting a file. Like the previous SQL cleanup it also treats direct
     * references from soft-deleted records as "in use", and additionally checks live HTML content.
     */
    public boolean isReferencedAnywhere(UUID id, String path) {
        if (id != null) {
            if (exists(PRODUCTS, Criteria.where("images.fileId").is(id))) return true;
            if (exists(VARIANTS, Criteria.where("imageFileId").is(id))) return true;
            if (exists(BRANDS, new Criteria().orOperator(Criteria.where("fileId").is(id), Criteria.where("fileSizeId").is(id)))) return true;
            if (exists(BANNERS, Criteria.where("fileId").is(id))) return true;
            if (exists(COLLECTIONS, Criteria.where("fileId").is(id))) return true;
            if (exists(BLOG_POSTS, Criteria.where("coverFileId").is(id))) return true;
        }
        return hasLiveHtmlReference(path);
    }

    private boolean hasLiveHtmlReference(String path) {
        if (path == null || path.isBlank()) return false;
        for (String collection : HTML_COLLECTIONS) {
            if (exists(collection, live().and("htmlFilePaths").is(path))) return true;
        }
        return false;
    }

    private boolean liveVariantReference(UUID fileId) {
        Query variants = Query.query(live().and("imageFileId").is(fileId));
        List<Object> productIds = mongo.findDistinct(variants, "productId", VARIANTS, Object.class);
        return !productIds.isEmpty() && exists(PRODUCTS, live().and("_id").in(productIds));
    }

    private boolean exists(String collection, Criteria criteria) {
        return mongo.exists(Query.query(criteria), collection);
    }

    private static Criteria live() {
        return Criteria.where("deleted").is(false);
    }
}
