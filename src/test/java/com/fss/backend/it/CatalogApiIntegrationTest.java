package com.fss.backend.it;

import com.fasterxml.jackson.databind.JsonNode;
import com.fss.backend.file.FileDocument;
import com.fss.backend.support.TestImages;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CatalogApiIntegrationTest extends MongoIntegrationTest {
    private static final Pattern UUID_PATTERN = Pattern.compile("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$");
    private static final Pattern UTC_TIME = Pattern.compile("^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(\\.\\d+)?Z$");

    @Test
    void productLifecycleKeepsApiContractFiltersAndFileReferences() throws Exception {
        JsonNode master = body(mvc.perform(admin(get("/api/admin/master-data"))).andExpect(status().isOk()).andReturn()).get("data");
        String size40 = idOf(master.get("sizes"), "value", "40");
        String size41 = idOf(master.get("sizes"), "value", "41");
        String black = idOf(master.get("colors"), "value", "black");
        String white = idOf(master.get("colors"), "value", "white");

        String categorySlug = unique("giay-chay");
        String categoryId = createId("/api/admin/categories", Map.of("name", "Giày chạy", "slug", categorySlug, "status", "ACTIVE"));
        String brandId = createId("/api/admin/brands", Map.of("name", unique("Brand")));

        JsonNode gallery = uploadImage(TestImages.png(2400, 1200, false), "gallery.png");
        JsonNode inline = uploadImage(TestImages.png(20, 20, false), "inline.png");
        assertTrue(UUID_PATTERN.matcher(gallery.get("fileId").asText()).matches());
        assertEquals("true", gallery.get("resized").asText());
        assertEquals("1600", gallery.get("width").asText());
        assertTrue(gallery.get("url").asText().startsWith("/files/"));
        assertEquals("INACTIVE", fileStatus(gallery));

        String keyword = unique("Pegasus");
        UUID variantClient = UUID.randomUUID();
        Map<String, Object> product = new java.util.HashMap<>(Map.of(
                "categoryId", categoryId, "brandId", brandId, "gender", "male", "name", keyword + " Running",
                "status", "ACTIVE", "shortDescription", "Êm chân",
                "descriptionHtml", "<p>Mô tả</p><img src=\"" + inline.get("url").asText() + "\"><script>x</script>",
                "images", List.of(Map.of("fileId", gallery.get("fileId").asText(), "isPrimary", true)),
                "variants", List.of(Map.of("clientId", variantClient.toString(), "name", "Đen", "colorId", black)),
                "skus", List.of(
                        Map.of("variantClientId", variantClient.toString(), "skuCode", unique("SKU"), "sizeId", size40,
                                "price", new BigDecimal("100.5"), "salePrice", new BigDecimal("90"), "stock", 5),
                        Map.of("variantClientId", variantClient.toString(), "skuCode", unique("SKU"), "sizeId", size41,
                                "price", new BigDecimal("200"), "stock", 3))));
        String productId = createId("/api/admin/products", product);

        // file references: attached files become ACTIVE
        assertEquals("ACTIVE", fileStatus(gallery));
        assertEquals("ACTIVE", fileStatus(inline));

        // ---- public list contract
        JsonNode page = getData("/api/products?keyword=" + keyword);
        assertEquals(1, page.get("total").asLong());
        assertEquals(1, page.get("page").asInt());
        assertEquals(20, page.get("limit").asInt());
        assertEquals(1, page.get("totalPages").asInt());
        JsonNode summary = page.get("items").get(0);
        assertEquals(productId, summary.get("id").asText());
        assertTrue(UUID_PATTERN.matcher(summary.get("categoryId").asText()).matches());
        assertEquals("Giày chạy", summary.get("categoryName").asText());
        assertEquals("MALE", summary.get("gender").asText());
        assertEquals(0, new BigDecimal("100.50").compareTo(summary.get("minPrice").decimalValue()));
        assertEquals("100.50", summary.get("minPrice").decimalValue().toPlainString());
        assertEquals("90.00", summary.get("minSalePrice").decimalValue().toPlainString());
        assertEquals(8, summary.get("totalStock").asInt());
        assertEquals(gallery.get("fileId").asText(), summary.get("primaryFileId").asText());
        assertEquals(gallery.get("url").asText(), summary.get("primaryImageUrl").asText());
        assertTrue(summary.get("brandSizeGuideUrl").isNull());
        assertTrue(UTC_TIME.matcher(summary.get("createdAt").asText()).matches(), summary.get("createdAt").asText());
        assertTrue(UTC_TIME.matcher(summary.get("updatedAt").asText()).matches(), summary.get("updatedAt").asText());

        // ---- filters (same semantics as the SQL version)
        assertEquals(1, getData("/api/products?keyword=" + keyword + "&size=40").get("total").asLong());
        assertEquals(0, getData("/api/products?keyword=" + keyword + "&size=42").get("total").asLong());
        assertEquals(1, getData("/api/products?keyword=" + keyword + "&color=BLA").get("total").asLong());
        assertEquals(0, getData("/api/products?keyword=" + keyword + "&color=" + "white").get("total").asLong());
        assertEquals(1, getData("/api/products?keyword=" + keyword + "&minPrice=150").get("total").asLong());
        assertEquals(0, getData("/api/products?keyword=" + keyword + "&maxPrice=50").get("total").asLong());
        assertEquals(1, getData("/api/products?keyword=" + keyword + "&categorySlug=" + categorySlug).get("total").asLong());
        assertEquals(1, getData("/api/products?keyword=" + keyword + "&gender=MALE,FEMALE").get("total").asLong());
        assertEquals(0, getData("/api/products?keyword=" + keyword + "&gender=FEMALE").get("total").asLong());
        assertEquals(1, getData("/api/products?keyword=" + keyword.toLowerCase() + "&brandId=" + brandId).get("total").asLong());
        assertEquals(0, getData("/api/products?categorySlug=" + unique("missing")).get("total").asLong());

        // ---- detail contract
        String slug = summary.get("slug").asText();
        JsonNode detail = getData("/api/products/" + slug);
        assertEquals("{}", detail.get("descriptionJson").asText());
        assertFalse(detail.get("descriptionHtml").asText().contains("<script"));
        assertTrue(detail.get("descriptionHtml").asText().contains(inline.get("url").asText()));
        assertEquals(1, detail.get("images").size());
        assertEquals(productId, detail.get("images").get(0).get("productId").asText());
        assertEquals("GALLERY", detail.get("images").get(0).get("imageType").asText());
        assertEquals("black", detail.get("variants").get(0).get("colorName").asText());
        assertEquals("#000000", detail.get("variants").get(0).get("colorCode").asText());
        assertEquals(List.of("40", "41"), List.of(detail.get("skus").get(0).get("size").asText(), detail.get("skus").get(1).get("size").asText()));
        assertEquals("100.50", detail.get("skus").get(0).get("price").decimalValue().toPlainString());

        // ---- validation & uniqueness
        mvc.perform(admin(jsonBody(post("/api/admin/products"), Map.of("categoryId", categoryId, "name", "Dup", "slug", slug))))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("SLUG_ALREADY_EXISTS"))
                .andExpect(jsonPath("$.message").value("Product slug already exists"));
        mvc.perform(admin(jsonBody(post("/api/admin/products"), Map.of("name", "No category"))))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR")).andExpect(jsonPath("$.errors[0].field").value("categoryId"));
        mvc.perform(get("/api/admin/products")).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("AUTH_REQUIRED"));

        // ---- update: replacing gallery image releases the old file, the HTML image stays in use
        JsonNode newGallery = uploadImage(TestImages.png(30, 30, false), "new.png");
        product.put("images", List.of(Map.of("fileId", newGallery.get("fileId").asText())));
        product.put("variants", List.of(Map.of("id", detail.get("variants").get(0).get("id").asText(), "name", "Trắng", "colorId", white)));
        product.put("skus", List.of(Map.of("id", detail.get("skus").get(0).get("id").asText(),
                "variantId", detail.get("variants").get(0).get("id").asText(), "skuCode", detail.get("skus").get(0).get("skuCode").asText(),
                "sizeId", size40, "price", new BigDecimal("100.5"), "stock", 7)));
        mvc.perform(admin(jsonBody(put("/api/admin/products/" + productId), product))).andExpect(status().isOk());
        assertEquals("INACTIVE", fileStatus(gallery));
        assertEquals("ACTIVE", fileStatus(newGallery));
        assertEquals("ACTIVE", fileStatus(inline));
        JsonNode updated = getData("/api/admin/products/" + productId);
        assertEquals(1, updated.get("skus").size());
        assertEquals(7, updated.get("skus").get(0).get("stock").asInt());
        assertTrue(updated.get("skus").get(0).get("salePrice").isNull());
        assertEquals("white", updated.get("variants").get(0).get("colorName").asText());
        assertEquals(1, getData("/api/products?keyword=" + keyword + "&color=white").get("total").asLong());

        // ---- delete
        mvc.perform(admin(delete("/api/admin/products/" + productId))).andExpect(status().isOk());
        mvc.perform(get("/api/products/" + slug)).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PRODUCT_NOT_FOUND")).andExpect(jsonPath("$.message").value("Product not found"));
        assertEquals("INACTIVE", fileStatus(inline));
    }

    @Test
    void paginationSortingAndFeaturedOrder() throws Exception {
        String categoryId = createId("/api/admin/categories", Map.of("name", unique("Cat")));
        String tag = unique("Paging");
        List<String> ids = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            ids.add(createId("/api/admin/products", Map.of("categoryId", categoryId, "name", tag + " " + i, "status", "ACTIVE",
                    "isFeatured", true, "featuredOrder", 3 - i)));
            Thread.sleep(5);
        }
        JsonNode first = getData("/api/products?keyword=" + tag + "&limit=2&page=1");
        JsonNode second = getData("/api/products?keyword=" + tag + "&limit=2&page=2");
        assertEquals(3, first.get("total").asLong());
        assertEquals(2, first.get("totalPages").asInt());
        assertEquals(ids.get(2), first.get("items").get(0).get("id").asText(), "newest first");
        assertEquals(1, second.get("items").size());
        assertEquals(ids.get(0), second.get("items").get(0).get("id").asText());

        JsonNode featured = body(mvc.perform(get("/api/products/featured?limit=30")).andReturn()).get("data");
        List<String> featuredIds = new ArrayList<>();
        featured.forEach(item -> featuredIds.add(item.get("id").asText()));
        assertTrue(featuredIds.indexOf(ids.get(2)) < featuredIds.indexOf(ids.get(0)), "featuredOrder ascending");

        // DRAFT products and products of inactive categories are hidden from the public API
        String hiddenCategory = createId("/api/admin/categories", Map.of("name", unique("Hidden"), "status", "INACTIVE"));
        createId("/api/admin/products", Map.of("categoryId", hiddenCategory, "name", tag + " hidden", "status", "ACTIVE"));
        createId("/api/admin/products", Map.of("categoryId", categoryId, "name", tag + " draft"));
        assertEquals(3, getData("/api/products?keyword=" + tag).get("total").asLong());
        assertEquals(5, getData("/api/admin/products?keyword=" + tag).get("total").asLong());
        assertEquals(1, getData("/api/admin/products?keyword=" + tag + "&status=DRAFT").get("total").asLong());
    }

    @Test
    void masterDataUniquenessIsCaseInsensitiveAndSoftDeleteFreesTheKey() throws Exception {
        String value = unique("XL");
        createId("/api/admin/sizes", Map.of("value", value));
        mvc.perform(admin(jsonBody(post("/api/admin/sizes"), Map.of("value", value.toLowerCase()))))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("SIZE_ALREADY_EXISTS"))
                .andExpect(jsonPath("$.message").value("Size already exists"));

        String customerPhone = "09" + (int) (Math.random() * 1_0000_0000);
        String customerId = createId("/api/admin/customers", Map.of("fullName", "A", "phone", customerPhone, "email", "A@Example.com"));
        mvc.perform(admin(jsonBody(post("/api/admin/customers"), Map.of("fullName", "B", "phone", customerPhone))))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("PHONE_ALREADY_EXISTS"))
                .andExpect(jsonPath("$.message").value("Phone already exists"));
        mvc.perform(admin(jsonBody(post("/api/admin/customers"), Map.of("fullName", "B", "phone", customerPhone + "1", "email", "a@example.COM"))))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("EMAIL_ALREADY_EXISTS"))
                .andExpect(jsonPath("$.message").value("Email already exists"));
        mvc.perform(admin(delete("/api/admin/customers/" + customerId))).andExpect(status().isOk());
        createId("/api/admin/customers", Map.of("fullName", "C", "phone", customerPhone));

        String code = unique("sale").toLowerCase();
        createId("/api/admin/coupons", Map.of("code", code, "discountType", "percent", "discountValue", 10));
        JsonNode coupons = getData("/api/admin/coupons?keyword=" + code);
        assertEquals(code.toUpperCase(), coupons.get("items").get(0).get("code").asText());
        assertEquals("10.00", coupons.get("items").get(0).get("discountValue").decimalValue().toPlainString());
        assertEquals(0, coupons.get("items").get(0).get("usedCount").asInt());
        JsonNode validated = body(mvc.perform(jsonBody(post("/api/coupons/validate"), Map.of("code", code, "subtotalAmount", "199.99")))
                .andExpect(status().isOk()).andReturn()).get("data");
        assertTrue(validated.get("valid").asBoolean());
        assertTrue(validated.get("code").isNull());
        assertEquals(code.toUpperCase(), validated.get("couponCode").asText());
        assertEquals("20.00", validated.get("discountAmount").decimalValue().toPlainString());
        assertEquals("179.99", validated.get("finalAmount").decimalValue().toPlainString());
    }

    @Test
    void contentModulesKeepUrlsAndHtmlReferences() throws Exception {
        JsonNode cover = uploadImage(TestImages.png(10, 10, false), "cover.png");
        JsonNode inline = uploadImage(TestImages.png(10, 10, false), "inline.png");
        String title = unique("Post");
        String postId = createId("/api/admin/blogs", Map.of("title", title, "status", "ACTIVE", "coverFileId", cover.get("fileId").asText(),
                "contentHtml", "<p>x</p><img src=\"" + inline.get("url").asText() + "\">"));
        JsonNode post = getData("/api/blogs?keyword=" + title).get("items").get(0);
        assertEquals(postId, post.get("id").asText());
        assertEquals(cover.get("url").asText(), post.get("coverImageUrl").asText());
        assertEquals("{}", post.get("contentJson").asText());
        assertNotNull(post.get("publishedAt").asText());
        assertTrue(UTC_TIME.matcher(post.get("updatedAt").asText()).matches(), post.get("updatedAt").asText());
        assertEquals("ACTIVE", fileStatus(inline));

        // Removing the image from HTML releases it; deleting the post releases the cover.
        mvc.perform(admin(jsonBody(put("/api/admin/blogs/" + postId), Map.of("title", title, "status", "ACTIVE",
                "coverFileId", cover.get("fileId").asText(), "contentHtml", "<p>no image</p>")))).andExpect(status().isOk());
        assertEquals("INACTIVE", fileStatus(inline));
        mvc.perform(admin(delete("/api/admin/blogs/" + postId))).andExpect(status().isOk());
        assertEquals("INACTIVE", fileStatus(cover));

        String bannerId = createId("/api/admin/landing-banners", Map.of("title", "B", "fileId", cover.get("fileId").asText()));
        JsonNode banners = getData("/api/landing-banners?limit=100");
        boolean found = false;
        for (JsonNode banner : banners.get("items")) {
            if (banner.get("id").asText().equals(bannerId)) {
                found = true;
                assertEquals(cover.get("url").asText(), banner.get("imageUrl").asText());
            }
        }
        assertTrue(found);
        assertEquals("ACTIVE", fileStatus(cover));

        String categoryId = createId("/api/admin/categories", Map.of("name", unique("Cat")));
        String p1 = createId("/api/admin/products", Map.of("categoryId", categoryId, "name", unique("P1"), "status", "ACTIVE"));
        String p2 = createId("/api/admin/products", Map.of("categoryId", categoryId, "name", unique("P2"), "status", "ACTIVE"));
        String draft = createId("/api/admin/products", Map.of("categoryId", categoryId, "name", unique("P3")));
        String collectionSlug = unique("summer");
        createId("/api/admin/collections", Map.of("name", "Summer", "slug", collectionSlug, "products", List.of(
                Map.of("productId", p1, "sortOrder", 2), Map.of("productId", p2, "sortOrder", 1), Map.of("productId", draft, "sortOrder", 0))));
        JsonNode collection = getData("/api/collections/" + collectionSlug);
        assertEquals(3, collection.get("productCount").asInt());
        assertTrue(UTC_TIME.matcher(collection.get("updatedAt").asText()).matches(), collection.get("updatedAt").asText());
        assertEquals(2, collection.get("products").get("total").asLong());
        assertEquals(p2, collection.get("products").get("items").get(0).get("id").asText());
        assertEquals(p1, collection.get("products").get("items").get(1).get("id").asText());
        assertNull(json.convertValue(collection.get("imageUrl"), String.class));
    }

    private String fileStatus(JsonNode upload) {
        FileDocument doc = mongo.findById(UUID.fromString(upload.get("fileId").asText()), FileDocument.class);
        return doc == null ? null : doc.status();
    }

    private String createId(String url, Object payload) throws Exception {
        JsonNode response = body(mvc.perform(admin(jsonBody(post(url), payload))).andExpect(status().isOk()).andReturn());
        String id = response.get("data").get("id").asText();
        assertTrue(UUID_PATTERN.matcher(id).matches(), id);
        return id;
    }

    private JsonNode getData(String url) throws Exception {
        return body(mvc.perform(admin(get(url))).andExpect(status().isOk()).andReturn()).get("data");
    }

    private static String idOf(JsonNode items, String field, String value) {
        for (JsonNode item : items) {
            if (value.equals(item.get(field).asText())) return item.get("id").asText();
        }
        throw new AssertionError(value + " not found");
    }
}
