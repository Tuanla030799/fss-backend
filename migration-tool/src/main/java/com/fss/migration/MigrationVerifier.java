package com.fss.migration;

import com.mongodb.client.MongoDatabase;
import org.bson.BsonValue;
import org.bson.Document;
import org.bson.types.Decimal128;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Read-only reconciliation between PostgreSQL and MongoDB: row counts per table (including embedded children),
 * business totals (stock, order amounts, coupon usage) and dangling file references. Exit code 1 on any mismatch.
 */
final class MigrationVerifier {
    private final Connection pg;
    private final MongoDatabase mongo;
    private final List<String> problems = new ArrayList<>();

    MigrationVerifier(Connection pg, MongoDatabase mongo) {
        this.pg = pg;
        this.mongo = mongo;
    }

    List<String> verify() throws SQLException {
        System.out.printf("%-34s %14s %14s %s%n", "check", "postgresql", "mongodb", "");
        for (String table : List.of("admin_users", "refresh_tokens", "customers", "files", "categories", "brands", "sizes",
                "colors", "products", "product_variants", "skus", "landing_banners", "coupons", "orders", "collections", "blog_posts")) {
            compare("count " + table, pgLong("SELECT COUNT(*) FROM " + table), mongo.getCollection(table).countDocuments());
        }
        compare("count live products", pgLong("SELECT COUNT(*) FROM products WHERE deleted_at IS NULL"),
                mongo.getCollection("products").countDocuments(new Document("deleted", false)));
        compare("count product_images", pgLong("SELECT COUNT(*) FROM product_images"), embedded("products", "images"));
        compare("count order_items", pgLong("SELECT COUNT(*) FROM order_items"), embedded("orders", "items"));
        compare("count order_status_history", pgLong("SELECT COUNT(*) FROM order_status_history"), embedded("orders", "statusHistory"));
        compare("count collection_products", pgLong("SELECT COUNT(*) FROM collection_products"), embedded("collections", "products"));

        compare("sum skus.stock (live)", pgDecimal("SELECT COALESCE(SUM(stock),0) FROM skus WHERE deleted_at IS NULL"),
                mongoSum("skus", "$stock", new Document("deleted", false)));
        compare("sum skus.price", pgDecimal("SELECT COALESCE(SUM(price),0) FROM skus"), mongoSum("skus", "$price", new Document()));
        compare("sum orders.total_amount", pgDecimal("SELECT COALESCE(SUM(total_amount),0) FROM orders"),
                mongoSum("orders", "$totalAmount", new Document()));
        compare("sum orders.discount_amount", pgDecimal("SELECT COALESCE(SUM(discount_amount),0) FROM orders"),
                mongoSum("orders", "$discountAmount", new Document()));
        compare("sum coupons.used_count", pgDecimal("SELECT COALESCE(SUM(used_count),0) FROM coupons"),
                mongoSum("coupons", "$usedCount", new Document()));
        compare("files ACTIVE", pgLong("SELECT COUNT(*) FROM files WHERE status='ACTIVE'"),
                mongo.getCollection("files").countDocuments(new Document("status", "ACTIVE")));

        long dangling = danglingFileReferences();
        compare("dangling file references", 0L, dangling);
        return problems;
    }

    private long danglingFileReferences() {
        long missing = 0;
        var files = mongo.getCollection("files");
        record Ref(String collection, String field) {}
        for (Ref ref : List.of(new Ref("products", "images.fileId"), new Ref("product_variants", "imageFileId"),
                new Ref("brands", "fileId"), new Ref("brands", "fileSizeId"), new Ref("landing_banners", "fileId"),
                new Ref("collections", "fileId"), new Ref("blog_posts", "coverFileId"))) {
            for (BsonValue id : mongo.getCollection(ref.collection()).distinct(ref.field(), BsonValue.class)) {
                if (id != null && !id.isNull() && files.countDocuments(new Document("_id", id)) == 0) {
                    missing++;
                    System.out.println("  missing file " + id + " referenced by " + ref.collection() + "." + ref.field());
                }
            }
        }
        return missing;
    }

    private long embedded(String collection, String field) {
        Document result = mongo.getCollection(collection).aggregate(List.of(
                new Document("$group", new Document("_id", null)
                        .append("n", new Document("$sum", new Document("$size", new Document("$ifNull", List.of("$" + field, List.of())))))))).first();
        return result == null ? 0 : ((Number) result.get("n")).longValue();
    }

    private BigDecimal mongoSum(String collection, String expression, Document match) {
        Document result = mongo.getCollection(collection).aggregate(List.of(new Document("$match", match),
                new Document("$group", new Document("_id", null).append("s", new Document("$sum", expression))))).first();
        if (result == null) return BigDecimal.ZERO;
        Object value = result.get("s");
        return value instanceof Decimal128 decimal ? decimal.bigDecimalValue() : new BigDecimal(value.toString());
    }

    private void compare(String check, Object expected, Object actual) {
        boolean ok = expected instanceof BigDecimal e && actual instanceof BigDecimal a ? e.compareTo(a) == 0
                : Objects.equals(expected instanceof Number n ? n.longValue() : expected, actual instanceof Number n2 ? n2.longValue() : actual);
        System.out.printf("%-34s %14s %14s %s%n", check, expected, actual, ok ? "OK" : "MISMATCH");
        if (!ok) problems.add(check + ": postgresql=" + expected + " mongodb=" + actual);
    }

    private long pgLong(String sql) throws SQLException {
        try (Statement statement = pg.createStatement(); ResultSet rs = statement.executeQuery(sql)) {
            rs.next();
            return rs.getLong(1);
        }
    }

    private BigDecimal pgDecimal(String sql) throws SQLException {
        try (Statement statement = pg.createStatement(); ResultSet rs = statement.executeQuery(sql)) {
            rs.next();
            return rs.getBigDecimal(1);
        }
    }
}
