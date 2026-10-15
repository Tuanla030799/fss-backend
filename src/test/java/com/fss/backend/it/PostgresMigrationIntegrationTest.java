package com.fss.backend.it;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fss.backend.auth.JwtProvider;
import com.fss.migration.MigrationCli;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end check of the data migration: legacy PostgreSQL schema (the old Flyway scripts) + sample data ->
 * migration-tool -> verify -> the application booted on the migrated database serves the same ids, prices and files.
 */
@SpringBootTest(properties = {"app.storage.provider=local", "app.upload-dir=build/tmp/it-uploads",
        "app.file-cleanup.initial-delay-ms=3600000", "app.seed.master-data=false"})
@AutoConfigureMockMvc
class PostgresMigrationIntegrationTest {
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");
    static final String DATABASE = "fss_migrated";
    /** The former Flyway scripts, now shipped with migration-tool as the legacy schema reference. */
    static final List<String> LEGACY_SCRIPTS = List.of("V1__init_schema.sql","V2__seed_admin.sql","V3__collections_and_blogs.sql","V4__brands.sql","V5__product_gender.sql","V6__drop_collection_description_json.sql","V7__sizes_and_colors_master_data.sql","V8__seed_size_color_master_data_and_drop_legacy_columns.sql","V9__normalize_seeded_size_values.sql","V10__strip_trailing_dot_from_size_values.sql","V11__cleanup_legacy_size_color_master_data.sql","V12__html_content_fields.sql","V13__clear_legacy_editor_json_content.sql","V14__collection_excerpt.sql","V15__collection_description_html.sql","V16__brand_size_guide_file.sql","V17__default_size_for_products_without_size.sql","V18__index_file_references.sql");
    static String firstRunOutput;
    static int verifyExit;
    static int secondRunExit;

    static {
        MongoIntegrationTest.MONGO.isRunning();
        POSTGRES.start();
        try {
            loadLegacySchemaAndData();
            String[] connection = {"--pg-url", POSTGRES.getJdbcUrl(), "--pg-user", POSTGRES.getUsername(),
                    "--pg-password", POSTGRES.getPassword(), "--mongo-uri", MongoIntegrationTest.MONGO.getReplicaSetUrl(DATABASE),
                    "--mongo-db", DATABASE};
            firstRunOutput = capture(() -> MigrationCli.run(concat("migrate-db", connection)));
            secondRunExit = MigrationCli.run(concat("migrate-db", connection)); // idempotent re-run
            verifyExit = MigrationCli.run(concat("verify", connection));
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    @DynamicPropertySource
    static void mongoProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.mongodb.uri", () -> MongoIntegrationTest.MONGO.getReplicaSetUrl(DATABASE));
        registry.add("spring.data.mongodb.database", () -> DATABASE);
    }

    @Autowired
    MockMvc mvc;
    @Autowired
    ObjectMapper json;
    @Autowired
    MongoTemplate mongo;
    @Autowired
    JwtProvider jwt;

    @Test
    void migratedDataIsServedWithSameIdsMoneyAndFiles() throws Exception {
        assertTrue(firstRunOutput.contains("products"), firstRunOutput);
        assertEquals(0, secondRunExit, "re-running the migration must be safe");
        assertEquals(0, verifyExit, "verify must report no mismatch");

        JsonNode product = data("/api/products/pegasus-41");
        assertEquals("b0000000-0000-0000-0000-000000000001", product.get("id").asText());
        assertEquals("Giày chạy", product.get("categoryName").asText());
        assertEquals("nike", product.get("brandSlug").asText());
        assertEquals("/files/2026-06-01/aaa-main.jpg", product.get("images").get(0).get("imageUrl").asText());
        assertEquals("11111111-1111-1111-1111-111111111111", product.get("images").get(0).get("fileId").asText());
        assertEquals("black", product.get("variants").get(0).get("colorName").asText());
        JsonNode sku = product.get("skus").get(0);
        assertEquals("40", sku.get("size").asText());
        assertEquals("3200000.00", sku.get("price").decimalValue().toPlainString());
        assertEquals("2990000.50", sku.get("salePrice").decimalValue().toPlainString());
        assertTrue(product.get("descriptionHtml").asText().contains("2026-06-01/bbb-inline%20image.png"));

        JsonNode list = data("/api/products?keyword=Pegasus");
        assertEquals(1, list.get("total").asLong(), "soft-deleted products stay hidden");
        assertEquals(7, list.get("items").get(0).get("totalStock").asInt());
        assertEquals("2990000.50", list.get("items").get(0).get("minSalePrice").decimalValue().toPlainString());

        JsonNode order = data("/api/admin/orders/a2000000-0000-0000-0000-000000000001");
        assertEquals("2721000.45", order.get("order").get("totalAmount").decimalValue().toPlainString());
        assertEquals("PEG-40", order.get("items").get(0).get("skuCode").asText());
        JsonNode history = data("/api/admin/orders/a2000000-0000-0000-0000-000000000001/status-history");
        assertEquals("CONFIRMED", history.get(0).get("newStatus").asText());
        assertEquals(2, history.size());

        JsonNode collection = data("/api/collections/summer");
        assertEquals(2, collection.get("productCount").asInt());
        assertEquals(1, collection.get("products").get("total").asLong());
        assertEquals("/files/2026-06-01/ccc-cover.jpg", data("/api/blogs/tin").get("coverImageUrl").asText());
        assertEquals(1, data("/api/admin/coupons?keyword=summer").get("items").get(0).get("usedCount").asInt());

        // HTML image references are indexed so the inline image is protected from cleanup.
        Document stored = mongo.getCollection("products").find(new Document("_id", UUID.fromString("b0000000-0000-0000-0000-000000000001"))).first();
        assertEquals(List.of("2026-06-01/bbb-inline image.png"), stored.getList("htmlFilePaths", String.class));
        // Seeded legacy admin can log in with its existing bcrypt hash (password not known here; check presence).
        assertEquals(1, mongo.getCollection("admin_users").countDocuments(new Document("emailKey", "tuanla0307@gmail.com")));
    }

    private JsonNode data(String url) throws Exception {
        byte[] body = mvc.perform(get(url).header("Authorization", "Bearer " + jwt.newAccessToken(UUID.randomUUID(), "super_admin")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        return json.copy().setNodeFactory(JsonNodeFactory.withExactBigDecimals(true))
                .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS).readTree(body).get("data");
    }

    private static void loadLegacySchemaAndData() throws Exception {
        try (Connection connection = DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
             Statement statement = connection.createStatement()) {
            for (String name : LEGACY_SCRIPTS) {
                statement.execute(new String(PostgresMigrationIntegrationTest.class.getResourceAsStream("/legacy-schema/" + name)
                        .readAllBytes(), StandardCharsets.UTF_8));
            }
            statement.execute(new String(PostgresMigrationIntegrationTest.class.getResourceAsStream("/migration/sample-data.sql")
                    .readAllBytes(), StandardCharsets.UTF_8));
        }
    }

    private static String[] concat(String command, String[] rest) {
        String[] all = new String[rest.length + 1];
        all[0] = command;
        System.arraycopy(rest, 0, all, 1, rest.length);
        return all;
    }

    private interface Action {
        int run() throws Exception;
    }

    private static String capture(Action action) throws Exception {
        PrintStream original = System.out;
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        System.setOut(new PrintStream(buffer, true, StandardCharsets.UTF_8));
        try {
            int code = action.run();
            if (code != 0) throw new IllegalStateException("exit " + code);
        } finally {
            System.setOut(original);
        }
        String output = buffer.toString(StandardCharsets.UTF_8);
        original.print(output);
        return output;
    }
}
