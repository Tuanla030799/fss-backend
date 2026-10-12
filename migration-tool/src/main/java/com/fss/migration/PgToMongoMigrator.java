package com.fss.migration;

import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.BulkWriteOptions;
import com.mongodb.client.model.ReplaceOneModel;
import com.mongodb.client.model.ReplaceOptions;
import com.mongodb.client.model.WriteModel;
import org.bson.Document;
import org.bson.types.Decimal128;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Copies every PostgreSQL table into the MongoDB document model used by the application.
 *
 * <ul>
 *   <li>UUIDs are kept as-is (BSON binary subtype 4), so ids, fileIds and relations stay valid.</li>
 *   <li>Soft-deleted rows are copied too ({@code deleted: true}) to keep history and references.</li>
 *   <li>Each document is written with {@code replaceOne(upsert)} by {@code _id}: the run is idempotent and can be
 *       repeated after fixing a problem. PostgreSQL is only read, never modified.</li>
 * </ul>
 * Run it before the API starts writing to MongoDB; re-running later would overwrite newer MongoDB changes of the same
 * documents with the PostgreSQL version.
 */
final class PgToMongoMigrator {
    private static final int BATCH = 500;

    private final Connection pg;
    private final MongoDatabase mongo;
    private final boolean dryRun;
    private final boolean allowExtraDocuments;
    private final Map<String, Integer> written = new java.util.LinkedHashMap<>();

    static final List<String> TABLES = List.of("admin_users", "refresh_tokens", "customers", "files", "categories", "brands",
            "sizes", "colors", "products", "product_variants", "skus", "landing_banners", "coupons", "orders", "collections",
            "blog_posts");

    PgToMongoMigrator(Connection pg, MongoDatabase mongo, boolean dryRun, boolean allowExtraDocuments) {
        this.pg = pg;
        this.mongo = mongo;
        this.dryRun = dryRun;
        this.allowExtraDocuments = allowExtraDocuments;
    }

    /**
     * The target must be empty or contain only documents from a previous run (same ids). Documents created by the
     * application itself (e.g. master data seeded on first boot) would collide with the migrated rows on unique keys.
     */
    List<String> extraDocuments() throws SQLException {
        List<String> problems = new ArrayList<>();
        for (String table : TABLES) {
            java.util.Set<UUID> pgIds = new java.util.HashSet<>();
            try (var statement = pg.createStatement(); var rs = statement.executeQuery("SELECT id FROM " + table)) {
                while (rs.next()) pgIds.add(rs.getObject(1, UUID.class));
            }
            long extra = 0;
            for (Document doc : mongo.getCollection(table).find().projection(new Document("_id", 1))) {
                if (!(doc.get("_id") instanceof UUID id) || !pgIds.contains(id)) extra++;
            }
            if (extra > 0) problems.add(table + ": " + extra + " document(s) not present in PostgreSQL");
        }
        return problems;
    }

    Map<String, Integer> migrateAll() throws SQLException {
        pg.setReadOnly(true);
        pg.setAutoCommit(false);
        // One consistent, read-only snapshot of all tables.
        pg.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
        List<String> extra = extraDocuments();
        if (!extra.isEmpty()) {
            String message = "Target MongoDB database is not empty: " + extra
                    + ". Migrate into a fresh database (drop it, or set APP_SEED_MASTER_DATA=false before the API first boots),"
                    + " or pass --allow-extra-documents if you are sure they do not conflict.";
            if (!allowExtraDocuments) throw new IllegalStateException(message);
            System.out.println("WARNING: " + message);
        }
        try {
            copy("admin_users", "SELECT * FROM admin_users", rs -> base(rs)
                    .append("name", rs.getString("name")).append("email", rs.getString("email"))
                    .append("emailKey", lower(rs.getString("email"))).append("passwordHash", rs.getString("password_hash"))
                    .append("role", rs.getString("role")).append("status", rs.getString("status")));
            copy("refresh_tokens", "SELECT * FROM refresh_tokens", rs -> new Document("_id", uuid(rs, "id"))
                    .append("adminId", uuid(rs, "admin_id")).append("token", rs.getString("token"))
                    .append("expiresAt", time(rs, "expires_at")).append("revokedAt", time(rs, "revoked_at"))
                    .append("createdAt", time(rs, "created_at")));
            copy("customers", "SELECT * FROM customers", rs -> {
                Document doc = base(rs).append("fullName", rs.getString("full_name")).append("email", rs.getString("email"))
                        .append("phone", rs.getString("phone")).append("passwordHash", rs.getString("password_hash"))
                        .append("status", rs.getString("status"));
                if (rs.getString("email") != null) doc.append("emailKey", lower(rs.getString("email")));
                return doc;
            });
            copy("files", "SELECT * FROM files", rs -> new Document("_id", uuid(rs, "id"))
                    .append("path", rs.getString("path")).append("objectKey", rs.getString("path"))
                    .append("storageProvider", "local").append("status", rs.getString("status"))
                    .append("contentType", contentType(rs.getString("path")))
                    .append("createdBy", uuid(rs, "created_by")).append("updatedBy", uuid(rs, "updated_by"))
                    .append("createdAt", time(rs, "created_at")).append("updatedAt", time(rs, "updated_at"))
                    .append("statusChangedAt", time(rs, "updated_at")).append("deleteAttempts", 0));
            copy("categories", "SELECT * FROM categories", rs -> audited(rs)
                    .append("parentId", uuid(rs, "parent_id")).append("name", rs.getString("name"))
                    .append("slug", rs.getString("slug")).append("description", rs.getString("description"))
                    .append("status", rs.getString("status")).append("sortOrder", rs.getInt("sort_order")));
            copy("brands", "SELECT * FROM brands", rs -> audited(rs)
                    .append("name", rs.getString("name")).append("slug", rs.getString("slug"))
                    .append("description", rs.getString("description")).append("fileId", uuid(rs, "file_id"))
                    .append("fileSizeId", uuid(rs, "file_size_id")).append("status", rs.getString("status"))
                    .append("sortOrder", rs.getInt("sort_order")));
            copy("sizes", "SELECT * FROM sizes", rs -> audited(rs)
                    .append("value", rs.getString("value")).append("valueKey", lower(rs.getString("value")))
                    .append("label", rs.getString("label")).append("status", rs.getString("status"))
                    .append("sortOrder", rs.getInt("sort_order")));
            copy("colors", "SELECT * FROM colors", rs -> audited(rs)
                    .append("name", rs.getString("name")).append("nameKey", lower(rs.getString("name")))
                    .append("colorCode", rs.getString("color_code")).append("status", rs.getString("status"))
                    .append("sortOrder", rs.getInt("sort_order")));

            Map<UUID, List<Document>> images = groupChildren(
                    "SELECT * FROM product_images ORDER BY product_id, sort_order, created_at, id", "product_id",
                    rs -> new Document("id", uuid(rs, "id")).append("fileId", uuid(rs, "file_id"))
                            .append("altText", rs.getString("alt_text")).append("imageType", rs.getString("image_type"))
                            .append("sortOrder", rs.getInt("sort_order")).append("isPrimary", rs.getBoolean("is_primary"))
                            .append("createdAt", time(rs, "created_at")));
            copy("products", "SELECT * FROM products", rs -> audited(rs)
                    .append("categoryId", uuid(rs, "category_id")).append("brandId", uuid(rs, "brand_id"))
                    .append("gender", rs.getString("gender")).append("name", rs.getString("name"))
                    .append("slug", rs.getString("slug")).append("shortDescription", rs.getString("short_description"))
                    .append("descriptionHtml", rs.getString("description_html"))
                    .append("htmlFilePaths", HtmlPaths.extract(rs.getString("description_html")))
                    .append("status", rs.getString("status")).append("isFeatured", rs.getBoolean("is_featured"))
                    .append("featuredOrder", rs.getInt("featured_order"))
                    .append("images", images.getOrDefault(uuid(rs, "id"), List.of())));
            copy("product_variants", "SELECT * FROM product_variants", rs -> base(rs)
                    .append("productId", uuid(rs, "product_id")).append("name", rs.getString("name"))
                    .append("colorId", uuid(rs, "color_id")).append("imageFileId", uuid(rs, "image_file_id"))
                    .append("status", rs.getString("status")).append("sortOrder", rs.getInt("sort_order")));
            copy("skus", "SELECT * FROM skus", rs -> {
                BigDecimal price = money(rs.getBigDecimal("price"));
                BigDecimal sale = money(rs.getBigDecimal("sale_price"));
                return base(rs).append("productId", uuid(rs, "product_id")).append("variantId", uuid(rs, "variant_id"))
                        .append("skuCode", rs.getString("sku_code")).append("sizeId", uuid(rs, "size_id"))
                        .append("price", decimal(price)).append("salePrice", decimal(sale))
                        .append("effectivePrice", decimal(sale != null ? sale : price))
                        .append("stock", rs.getInt("stock")).append("status", rs.getString("status"));
            });
            copy("landing_banners", "SELECT * FROM landing_banners", rs -> audited(rs)
                    .append("title", rs.getString("title")).append("subtitle", rs.getString("subtitle"))
                    .append("linkUrl", rs.getString("link_url")).append("fileId", uuid(rs, "file_id"))
                    .append("status", rs.getString("status")).append("sortOrder", rs.getInt("sort_order"))
                    .append("startsAt", time(rs, "starts_at")).append("endsAt", time(rs, "ends_at")));
            copy("coupons", "SELECT * FROM coupons", rs -> audited(rs)
                    .append("code", upper(rs.getString("code"))).append("name", rs.getString("name"))
                    .append("discountType", rs.getString("discount_type"))
                    .append("discountValue", decimal(money(rs.getBigDecimal("discount_value"))))
                    .append("maxDiscount", decimal(money(rs.getBigDecimal("max_discount"))))
                    .append("minOrderAmount", decimal(money(rs.getBigDecimal("min_order_amount"))))
                    .append("usageLimit", integer(rs, "usage_limit")).append("usedCount", rs.getInt("used_count"))
                    .append("startsAt", time(rs, "starts_at")).append("endsAt", time(rs, "ends_at"))
                    .append("status", rs.getString("status")));

            Map<UUID, List<Document>> items = groupChildren("SELECT * FROM order_items ORDER BY order_id, created_at, id", "order_id",
                    rs -> new Document("id", uuid(rs, "id")).append("skuId", uuid(rs, "sku_id"))
                            .append("productName", rs.getString("product_name")).append("skuCode", rs.getString("sku_code"))
                            .append("variantName", rs.getString("variant_name")).append("size", rs.getString("size"))
                            .append("unitPrice", decimal(money(rs.getBigDecimal("unit_price"))))
                            .append("quantity", rs.getInt("quantity"))
                            .append("lineTotal", decimal(money(rs.getBigDecimal("line_total"))))
                            .append("createdAt", time(rs, "created_at")));
            Map<UUID, List<Document>> history = groupChildren(
                    "SELECT * FROM order_status_history ORDER BY order_id, created_at, id", "order_id",
                    rs -> new Document("id", uuid(rs, "id")).append("oldStatus", rs.getString("old_status"))
                            .append("newStatus", rs.getString("new_status")).append("note", rs.getString("note"))
                            .append("createdBy", uuid(rs, "created_by")).append("createdAt", time(rs, "created_at")));
            copy("orders", "SELECT * FROM orders", rs -> base(rs)
                    .append("orderCode", rs.getString("order_code")).append("customerName", rs.getString("customer_name"))
                    .append("customerPhone", rs.getString("customer_phone")).append("customerEmail", rs.getString("customer_email"))
                    .append("shippingAddress", rs.getString("shipping_address")).append("note", rs.getString("note"))
                    .append("status", rs.getString("status")).append("couponId", uuid(rs, "coupon_id"))
                    .append("customerId", uuid(rs, "customer_id")).append("paymentMethod", rs.getString("payment_method"))
                    .append("paymentStatus", rs.getString("payment_status")).append("paidAt", time(rs, "paid_at"))
                    .append("shippingMethod", rs.getString("shipping_method")).append("shippingStatus", rs.getString("shipping_status"))
                    .append("trackingCode", rs.getString("tracking_code")).append("internalNote", rs.getString("internal_note"))
                    .append("subtotalAmount", decimal(money(rs.getBigDecimal("subtotal_amount"))))
                    .append("discountAmount", decimal(money(rs.getBigDecimal("discount_amount"))))
                    .append("shippingFee", decimal(money(rs.getBigDecimal("shipping_fee"))))
                    .append("totalAmount", decimal(money(rs.getBigDecimal("total_amount"))))
                    .append("items", items.getOrDefault(uuid(rs, "id"), List.of()))
                    .append("statusHistory", history.getOrDefault(uuid(rs, "id"), List.of())));

            Map<UUID, List<Document>> members = groupChildren(
                    "SELECT * FROM collection_products ORDER BY collection_id, sort_order, created_at, product_id", "collection_id",
                    rs -> new Document("productId", uuid(rs, "product_id")).append("sortOrder", rs.getInt("sort_order"))
                            .append("createdAt", time(rs, "created_at")));
            copy("collections", "SELECT * FROM collections", rs -> {
                String html = rs.getString("description_html") != null ? rs.getString("description_html") : rs.getString("description");
                return audited(rs).append("name", rs.getString("name")).append("slug", rs.getString("slug"))
                        .append("excerpt", rs.getString("excerpt")).append("descriptionHtml", html)
                        .append("htmlFilePaths", HtmlPaths.extract(html)).append("fileId", uuid(rs, "file_id"))
                        .append("status", rs.getString("status")).append("sortOrder", rs.getInt("sort_order"))
                        .append("products", members.getOrDefault(uuid(rs, "id"), List.of()));
            });
            copy("blog_posts", "SELECT * FROM blog_posts", rs -> audited(rs)
                    .append("title", rs.getString("title")).append("slug", rs.getString("slug"))
                    .append("excerpt", rs.getString("excerpt")).append("contentHtml", rs.getString("content_html"))
                    .append("htmlFilePaths", HtmlPaths.extract(rs.getString("content_html")))
                    .append("coverFileId", uuid(rs, "cover_file_id")).append("status", rs.getString("status"))
                    .append("publishedAt", time(rs, "published_at")));
        } finally {
            pg.rollback();
        }
        return written;
    }

    private interface RowMapper {
        Document map(ResultSet rs) throws SQLException;
    }

    private void copy(String collection, String sql, RowMapper mapper) throws SQLException {
        int count = 0;
        List<WriteModel<Document>> batch = new ArrayList<>(BATCH);
        try (PreparedStatement statement = pg.prepareStatement(sql)) {
            statement.setFetchSize(BATCH);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    Document doc = mapper.map(rs);
                    batch.add(new ReplaceOneModel<>(new Document("_id", doc.get("_id")), doc, new ReplaceOptions().upsert(true)));
                    count++;
                    if (batch.size() == BATCH) flush(collection, batch);
                }
            }
        }
        flush(collection, batch);
        written.put(collection, count);
        System.out.printf("%-18s %8d document(s)%s%n", collection, count, dryRun ? " (dry run, not written)" : "");
    }

    private void flush(String collection, List<WriteModel<Document>> batch) {
        if (!batch.isEmpty() && !dryRun) {
            mongo.getCollection(collection).bulkWrite(batch, new BulkWriteOptions().ordered(false));
        }
        batch.clear();
    }

    private Map<UUID, List<Document>> groupChildren(String sql, String parentColumn, RowMapper mapper) throws SQLException {
        Map<UUID, List<Document>> result = new HashMap<>();
        try (PreparedStatement statement = pg.prepareStatement(sql)) {
            statement.setFetchSize(BATCH);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    result.computeIfAbsent(uuid(rs, parentColumn), key -> new ArrayList<>()).add(mapper.map(rs));
                }
            }
        }
        return result;
    }

    /** id + timestamps + soft delete, shared by every soft-deletable table. */
    private static Document base(ResultSet rs) throws SQLException {
        Instant deletedAt = hasColumn(rs, "deleted_at") ? time(rs, "deleted_at") : null;
        return new Document("_id", uuid(rs, "id"))
                .append("createdAt", time(rs, "created_at"))
                .append("updatedAt", hasColumn(rs, "updated_at") ? time(rs, "updated_at") : time(rs, "created_at"))
                .append("deleted", deletedAt != null)
                .append("deletedAt", deletedAt);
    }

    private static Document audited(ResultSet rs) throws SQLException {
        return base(rs).append("createdBy", uuid(rs, "created_by")).append("updatedBy", uuid(rs, "updated_by"));
    }

    private static boolean hasColumn(ResultSet rs, String column) throws SQLException {
        var meta = rs.getMetaData();
        for (int i = 1; i <= meta.getColumnCount(); i++) {
            if (meta.getColumnName(i).equalsIgnoreCase(column)) return true;
        }
        return false;
    }

    static UUID uuid(ResultSet rs, String column) throws SQLException {
        return rs.getObject(column, UUID.class);
    }

    static Instant time(ResultSet rs, String column) throws SQLException {
        OffsetDateTime value = rs.getObject(column, OffsetDateTime.class);
        return value == null ? null : value.toInstant();
    }

    private static Integer integer(ResultSet rs, String column) throws SQLException {
        int value = rs.getInt(column);
        return rs.wasNull() ? null : value;
    }

    static BigDecimal money(BigDecimal value) {
        return value == null ? null : value.setScale(2, RoundingMode.HALF_UP);
    }

    static Decimal128 decimal(BigDecimal value) {
        return value == null ? null : new Decimal128(value);
    }

    private static String lower(String value) {
        return value == null ? null : value.trim().toLowerCase(Locale.ROOT);
    }

    private static String upper(String value) {
        return value == null ? null : value.toUpperCase(Locale.ROOT);
    }

    static String contentType(String path) {
        String lower = path == null ? "" : path.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return "image/jpeg";
        if (lower.endsWith(".png")) return "image/png";
        if (lower.endsWith(".gif")) return "image/gif";
        if (lower.endsWith(".webp")) return "image/webp";
        if (lower.endsWith(".svg")) return "image/svg+xml";
        return "application/octet-stream";
    }

}
