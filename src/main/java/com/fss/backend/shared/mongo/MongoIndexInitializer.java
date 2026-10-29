package com.fss.backend.shared.mongo;

import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.index.IndexOperations;
import org.springframework.data.mongodb.core.index.PartialIndexFilter;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * Creates the indexes the queries rely on. {@code createIndex} is idempotent and cheap when the index exists, so
 * this runs on boot (it is not a data migration). Uniqueness that PostgreSQL enforced with partial unique indexes
 * ({@code WHERE deleted_at IS NULL}) is enforced here with unique indexes filtered on {@code deleted: false}.
 */
@Component
@Order(0)
@ConditionalOnProperty(name = "app.mongodb.ensure-indexes", havingValue = "true", matchIfMissing = true)
public class MongoIndexInitializer implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(MongoIndexInitializer.class);

    private final MongoTemplate mongo;

    public MongoIndexInitializer(MongoTemplate mongo) {
        this.mongo = mongo;
    }

    @Override
    public void run(ApplicationArguments args) {
        ensureIndexes();
    }

    public void ensureIndexes() {
        // auth
        uniqueLive("admin_users", "emailKey");
        index("admin_users", new Index().on("deleted", Sort.Direction.ASC).on("createdAt", Sort.Direction.DESC));
        index("refresh_tokens", new Index().on("token", Sort.Direction.ASC).unique());
        index("refresh_tokens", new Index().on("expiresAt", Sort.Direction.ASC).expire(0, TimeUnit.SECONDS));

        // customers
        uniqueLive("customers", "phone");
        index("customers", new Index().on("emailKey", Sort.Direction.ASC).unique()
                .partial(PartialIndexFilter.of(Criteria.where("deleted").is(false).and("emailKey").type(2))));
        index("customers", new Index().on("deleted", Sort.Direction.ASC).on("createdAt", Sort.Direction.DESC));

        // files
        index("files", new Index().on("path", Sort.Direction.ASC));
        index("files", new Index().on("status", Sort.Direction.ASC).on("statusChangedAt", Sort.Direction.ASC));
        index("files", new Index().on("status", Sort.Direction.ASC).on("leaseUntil", Sort.Direction.ASC));
        index("files", new Index().on("status", Sort.Direction.ASC).on("createdAt", Sort.Direction.ASC));

        // catalog
        uniqueLive("categories", "slug");
        index("categories", new Index().on("deleted", Sort.Direction.ASC).on("status", Sort.Direction.ASC).on("sortOrder", Sort.Direction.ASC));
        uniqueLive("brands", "slug");
        index("brands", new Index().on("deleted", Sort.Direction.ASC).on("status", Sort.Direction.ASC).on("sortOrder", Sort.Direction.ASC));
        index("brands", new Index().on("fileId", Sort.Direction.ASC));
        index("brands", new Index().on("fileSizeId", Sort.Direction.ASC));
        uniqueLive("sizes", "valueKey");
        index("sizes", new Index().on("value", Sort.Direction.ASC));
        uniqueLive("colors", "nameKey");

        uniqueLive("products", "slug");
        index("products", new Index().on("deleted", Sort.Direction.ASC).on("status", Sort.Direction.ASC).on("createdAt", Sort.Direction.DESC));
        index("products", new Index().on("deleted", Sort.Direction.ASC).on("isFeatured", Sort.Direction.ASC).on("featuredOrder", Sort.Direction.ASC).on("createdAt", Sort.Direction.DESC));
        index("products", new Index().on("categoryId", Sort.Direction.ASC).on("status", Sort.Direction.ASC).on("createdAt", Sort.Direction.DESC));
        index("products", new Index().on("brandId", Sort.Direction.ASC).on("status", Sort.Direction.ASC).on("createdAt", Sort.Direction.DESC));
        index("products", new Index().on("images.fileId", Sort.Direction.ASC));
        index("products", new Index().on("htmlFilePaths", Sort.Direction.ASC));

        index("product_variants", new Index().on("productId", Sort.Direction.ASC).on("deleted", Sort.Direction.ASC).on("sortOrder", Sort.Direction.ASC));
        index("product_variants", new Index().on("colorId", Sort.Direction.ASC).on("deleted", Sort.Direction.ASC));
        index("product_variants", new Index().on("imageFileId", Sort.Direction.ASC));

        uniqueLive("skus", "skuCode");
        index("skus", new Index().on("productId", Sort.Direction.ASC).on("deleted", Sort.Direction.ASC));
        index("skus", new Index().on("sizeId", Sort.Direction.ASC).on("deleted", Sort.Direction.ASC));
        index("skus", new Index().on("effectivePrice", Sort.Direction.ASC).on("deleted", Sort.Direction.ASC));

        // content
        index("landing_banners", new Index().on("deleted", Sort.Direction.ASC).on("status", Sort.Direction.ASC).on("sortOrder", Sort.Direction.ASC));
        index("landing_banners", new Index().on("fileId", Sort.Direction.ASC));
        index("landing_banners", new Index().on("mobileFileId", Sort.Direction.ASC));
        uniqueLive("collections", "slug");
        index("collections", new Index().on("deleted", Sort.Direction.ASC).on("status", Sort.Direction.ASC).on("sortOrder", Sort.Direction.ASC));
        index("collections", new Index().on("fileId", Sort.Direction.ASC));
        index("collections", new Index().on("htmlFilePaths", Sort.Direction.ASC));
        uniqueLive("blog_posts", "slug");
        index("blog_posts", new Index().on("deleted", Sort.Direction.ASC).on("status", Sort.Direction.ASC).on("publishedAt", Sort.Direction.DESC));
        index("blog_posts", new Index().on("coverFileId", Sort.Direction.ASC));
        index("blog_posts", new Index().on("htmlFilePaths", Sort.Direction.ASC));
        index("site_pages", new Index().on("htmlFilePaths", Sort.Direction.ASC));
        index("faqs", new Index().on("deleted", Sort.Direction.ASC).on("status", Sort.Direction.ASC).on("sortOrder", Sort.Direction.ASC));
        index("faqs", new Index().on("htmlFilePaths", Sort.Direction.ASC));

        // promotion + sales
        uniqueLive("coupons", "code");
        index("coupons", new Index().on("deleted", Sort.Direction.ASC).on("status", Sort.Direction.ASC).on("createdAt", Sort.Direction.DESC));
        index("orders", new Index().on("orderCode", Sort.Direction.ASC).unique());
        index("orders", new Index().on("deleted", Sort.Direction.ASC).on("status", Sort.Direction.ASC).on("createdAt", Sort.Direction.DESC));
        index("orders", new Index().on("customerPhone", Sort.Direction.ASC).on("createdAt", Sort.Direction.DESC));
        log.info("MongoDB indexes ensured");
    }

    private void uniqueLive(String collection, String field) {
        index(collection, new Index().on(field, Sort.Direction.ASC).unique()
                .named("uq_" + field + "_live")
                .partial(PartialIndexFilter.of(new Document("deleted", false))));
    }

    private void index(String collection, Index index) {
        IndexOperations operations = mongo.indexOps(collection);
        operations.ensureIndex(index);
    }
}
