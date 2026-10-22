package com.fss.backend.it;

import com.fasterxml.jackson.databind.JsonNode;
import com.fss.backend.auth.AuthRepository;
import com.fss.backend.auth.RefreshTokenCookie;
import com.fss.backend.promotion.coupon.CouponDocument;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Contract points the frontend relies on: error codes, coupon checks, sorting, updatedAt, public visibility, auth cookie. */
class ApiContractIntegrationTest extends MongoIntegrationTest {
    @Autowired
    private AuthRepository authRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    void errorsCarryStatusCodeAndFieldErrors() throws Exception {
        JsonNode notFound = error(get("/api/products/" + unique("missing")), 404, "PRODUCT_NOT_FOUND");
        assertEquals("Product not found", notFound.get("message").asText());
        assertFalse(notFound.has("errors"), "errors is omitted when there are no field errors");
        error(admin(get("/api/admin/products/" + UUID.randomUUID())), 404, "PRODUCT_NOT_FOUND");
        error(get("/api/blogs/" + unique("missing")), 404, "BLOG_POST_NOT_FOUND");
        error(get("/api/collections/" + unique("missing")), 404, "COLLECTION_NOT_FOUND");
        error(admin(get("/api/admin/orders/" + UUID.randomUUID())), 404, "ORDER_NOT_FOUND");

        // A missing reference inside the body is a bad request, not a missing resource.
        error(admin(jsonBody(post("/api/admin/products"), Map.of("categoryId", UUID.randomUUID(), "name", "X"))),
                400, "CATEGORY_NOT_FOUND");

        String slug = unique("cat");
        createId("/api/admin/categories", Map.of("name", "Cat", "slug", slug));
        error(admin(jsonBody(post("/api/admin/categories"), Map.of("name", "Cat 2", "slug", slug))), 409, "SLUG_ALREADY_EXISTS");

        JsonNode invalid = error(admin(jsonBody(post("/api/admin/products"), Map.of())), 400, "VALIDATION_ERROR");
        List<String> fields = new ArrayList<>();
        invalid.get("errors").forEach(fieldError -> fields.add(fieldError.get("field").asText()));
        assertTrue(fields.containsAll(List.of("categoryId", "name")), fields.toString());

        JsonNode badParam = error(get("/api/products?page=abc"), 400, "VALIDATION_ERROR");
        assertEquals("page", badParam.get("errors").get(0).get("field").asText());

        error(post("/api/orders").contentType(MediaType.APPLICATION_JSON).content("{not json"), 400, "INVALID_REQUEST_BODY");
        error(get("/api/admin/products"), 401, "AUTH_REQUIRED");
        error(get("/api/admin/products").header(HttpHeaders.AUTHORIZATION, "Bearer not-a-token"), 401, "AUTH_TOKEN_INVALID");
    }

    @Test
    void couponValidationExplainsWhyACodeCannotBeUsed() throws Exception {
        String ok = coupon(Map.of());
        JsonNode valid = validateCoupon("  " + ok.toLowerCase() + " ", "200000");
        assertTrue(valid.get("valid").asBoolean());
        assertTrue(valid.get("code").isNull());
        assertTrue(valid.get("message").isNull());
        assertEquals(ok, valid.get("couponCode").asText());
        assertEquals("20000.00", valid.get("discountAmount").decimalValue().toPlainString());
        assertEquals("180000.00", valid.get("finalAmount").decimalValue().toPlainString());

        String tomorrow = OffsetDateTime.now(ZoneOffset.UTC).plusDays(1).toString();
        String yesterday = OffsetDateTime.now(ZoneOffset.UTC).minusDays(1).toString();
        assertRejected(validateCoupon(unique("NOPE"), "200000"), "COUPON_NOT_FOUND");
        assertRejected(validateCoupon(coupon(Map.of("status", "INACTIVE")), "200000"), "COUPON_INVALID");
        assertRejected(validateCoupon(coupon(Map.of("startsAt", tomorrow)), "200000"), "COUPON_NOT_STARTED");
        String expired = coupon(Map.of("endsAt", yesterday));
        assertRejected(validateCoupon(expired, "200000"), "COUPON_EXPIRED");
        JsonNode belowMinimum = validateCoupon(coupon(Map.of("minOrderAmount", 500000)), "200000");
        assertRejected(belowMinimum, "COUPON_MIN_SUBTOTAL");
        assertEquals("Đơn hàng cần tối thiểu 500.000 đ để dùng mã này.", belowMinimum.get("message").asText());
        String usedUp = coupon(Map.of("usageLimit", 1));
        mongo.updateFirst(Query.query(Criteria.where("code").is(usedUp)), Update.update("usedCount", 1), CouponDocument.class);
        assertRejected(validateCoupon(usedUp, "200000"), "COUPON_USAGE_LIMIT_REACHED");

        // Placing an order with the same code fails with the same reason.
        String productId = product(createId("/api/admin/categories", Map.of("name", unique("Cat"))), unique("Coupon"),
                Map.of("skus", List.of(sku("100", null, 5))));
        String skuId = getData("/api/admin/products/" + productId).get("skus").get(0).get("id").asText();
        Map<String, Object> order = new HashMap<>(order(skuId));
        order.put("couponCode", expired);
        error(jsonBody(post("/api/orders"), order), 400, "COUPON_EXPIRED");
    }

    @Test
    void publicListSortsTheWholeFilteredListBeforePaging() throws Exception {
        String categoryId = createId("/api/admin/categories", Map.of("name", unique("Cat")));
        String tag = unique("Sort");
        // Created oldest to newest. Selling price is the sale price when there is one.
        String cheap = product(categoryId, tag + " b10", Map.of("skus", List.of(sku("300", "100", 1))));
        String pricey = product(categoryId, tag + " B9", Map.of("isFeatured", true, "featuredOrder", 2,
                "skus", List.of(sku("200", null, 1))));
        String middle = product(categoryId, tag + " a", Map.of("isFeatured", true, "featuredOrder", 1,
                "skus", List.of(sku("150", null, 1), sku("400", null, 1))));
        String noSku = product(categoryId, tag + " c", Map.of());

        String list = "/api/products?keyword=" + tag;
        assertEquals(List.of(noSku, middle, pricey, cheap), ids(list), "newest first by default");
        assertEquals(List.of(noSku, middle, pricey, cheap), ids(list + "&sort=newest"));
        assertEquals(List.of(middle, pricey, noSku, cheap), ids(list + "&sort=featured"));
        assertEquals(List.of(cheap, middle, pricey, noSku), ids(list + "&sort=price_asc"), "no price comes last");
        assertEquals(List.of(pricey, middle, cheap, noSku), ids(list + "&sort=price_desc"), "no price comes last");
        // Vietnamese collation: case-insensitive, numbers compared as numbers (B9 before b10).
        assertEquals(List.of(middle, pricey, cheap, noSku), ids(list + "&sort=name_asc"));
        assertEquals(List.of(cheap, middle, pricey, noSku), ids(list + "&sort=PRICE-ASC"));

        // The whole filtered list is sorted before paging.
        assertEquals(List.of(cheap, middle), ids(list + "&sort=price_asc&limit=2&page=1"));
        assertEquals(List.of(pricey, noSku), ids(list + "&sort=price_asc&limit=2&page=2"));
        assertEquals(List.of(cheap), ids(list + "&sort=price_asc&maxPrice=120"));

        JsonNode invalid = error(get(list + "&sort=cheapest"), 400, "VALIDATION_ERROR");
        assertEquals("sort must be one of: featured, newest, price_asc, price_desc, name_asc", invalid.get("message").asText());
    }

    @Test
    void productUpdatedAtFollowsSkuChanges() throws Exception {
        String name = unique("Updated");
        String productId = product(createId("/api/admin/categories", Map.of("name", unique("Cat"))), name,
                Map.of("skus", List.of(sku("100", null, 5))));
        JsonNode detail = getData("/api/admin/products/" + productId);
        Instant created = Instant.parse(detail.get("createdAt").asText());
        Instant first = Instant.parse(detail.get("updatedAt").asText());
        assertFalse(first.isBefore(created));

        JsonNode sku = detail.get("skus").get(0);
        Thread.sleep(20);
        mvc.perform(admin(jsonBody(put("/api/admin/products/" + productId + "/skus/" + sku.get("id").asText()), Map.of(
                "skuCode", sku.get("skuCode").asText(), "sizeId", sku.get("sizeId").asText(),
                "price", new BigDecimal("100"), "stock", 9)))).andExpect(status().isOk());
        Instant afterSkuEdit = Instant.parse(getData("/api/admin/products/" + productId).get("updatedAt").asText());
        assertTrue(afterSkuEdit.isAfter(first), first + " -> " + afterSkuEdit);
        JsonNode summary = getData("/api/products?keyword=" + name).get("items").get(0);
        assertEquals(afterSkuEdit, Instant.parse(summary.get("updatedAt").asText()));

        // A sale changes the stock shown on the page, so it counts as an update too.
        Thread.sleep(20);
        mvc.perform(jsonBody(post("/api/orders"), order(sku.get("id").asText()))).andExpect(status().isOk());
        Instant afterOrder = Instant.parse(getData("/api/products?keyword=" + name).get("items").get(0).get("updatedAt").asText());
        assertTrue(afterOrder.isAfter(afterSkuEdit), afterSkuEdit + " -> " + afterOrder);
    }

    @Test
    void publicApiHidesInactiveVariantsSkusAndUnpublishedContent() throws Exception {
        JsonNode master = getData("/api/admin/master-data");
        String black = idOf(master.get("colors"), "black");
        String white = idOf(master.get("colors"), "white");
        String name = unique("Visible");
        UUID activeVariant = UUID.randomUUID();
        UUID inactiveVariant = UUID.randomUUID();
        String productId = product(createId("/api/admin/categories", Map.of("name", unique("Cat"))), name, Map.of(
                "variants", List.of(
                        Map.of("clientId", activeVariant.toString(), "name", "Đen", "colorId", black),
                        Map.of("clientId", inactiveVariant.toString(), "name", "Trắng", "colorId", white, "status", "INACTIVE")),
                "skus", List.of(
                        variantSku(activeVariant, "100", 2, "ACTIVE"),
                        variantSku(activeVariant, "40", 4, "INACTIVE"),
                        variantSku(inactiveVariant, "50", 3, "ACTIVE"))));

        JsonNode summary = getData("/api/products?keyword=" + name).get("items").get(0);
        assertEquals("100.00", summary.get("minPrice").decimalValue().toPlainString());
        assertEquals(2, summary.get("totalStock").asInt());
        JsonNode adminSummary = getData("/api/admin/products?keyword=" + name).get("items").get(0);
        assertEquals("40.00", adminSummary.get("minPrice").decimalValue().toPlainString());
        assertEquals(9, adminSummary.get("totalStock").asInt());

        JsonNode detail = getData("/api/products/" + summary.get("slug").asText());
        assertEquals(1, detail.get("variants").size());
        assertEquals("Đen", detail.get("variants").get(0).get("name").asText());
        assertEquals(1, detail.get("skus").size());
        assertEquals("100.00", detail.get("skus").get(0).get("price").decimalValue().toPlainString());
        JsonNode adminDetail = getData("/api/admin/products/" + productId);
        assertEquals(2, adminDetail.get("variants").size());
        assertEquals(3, adminDetail.get("skus").size());

        // Filters only see what is on sale.
        assertEquals(0, getData("/api/products?keyword=" + name + "&maxPrice=60").get("total").asLong());
        assertEquals(1, getData("/api/products?keyword=" + name + "&maxPrice=100").get("total").asLong());
        assertEquals(0, getData("/api/products?keyword=" + name + "&color=white").get("total").asLong());

        // Hidden SKUs cannot be ordered either.
        for (JsonNode sku : adminDetail.get("skus")) {
            if (sku.get("price").decimalValue().compareTo(new BigDecimal("100")) != 0) {
                error(jsonBody(post("/api/orders"), order(sku.get("id").asText())), 400, "SKU_UNAVAILABLE");
            }
        }

        String tomorrow = OffsetDateTime.now(ZoneOffset.UTC).plusDays(1).toString();
        String yesterday = OffsetDateTime.now(ZoneOffset.UTC).minusDays(1).toString();
        String scheduled = unique("Scheduled");
        createId("/api/admin/blogs", Map.of("title", scheduled, "slug", scheduled.toLowerCase(), "status", "ACTIVE",
                "publishedAt", tomorrow, "contentHtml", "<p>x</p>"));
        String draft = unique("Draft");
        createId("/api/admin/blogs", Map.of("title", draft, "contentHtml", "<p>x</p>"));
        assertEquals(0, getData("/api/blogs?keyword=" + scheduled).get("total").asLong());
        assertEquals(0, getData("/api/blogs?keyword=" + draft).get("total").asLong());
        error(get("/api/blogs/" + scheduled.toLowerCase()), 404, "BLOG_POST_NOT_FOUND");
        assertEquals(1, getData("/api/admin/blogs?keyword=" + scheduled).get("total").asLong());

        String notStarted = createId("/api/admin/landing-banners", Map.of("title", unique("Soon"), "startsAt", tomorrow));
        String ended = createId("/api/admin/landing-banners", Map.of("title", unique("Over"), "endsAt", yesterday));
        String running = createId("/api/admin/landing-banners", Map.of("title", unique("Now"), "startsAt", yesterday, "endsAt", tomorrow));
        List<String> banners = ids("/api/landing-banners?limit=100");
        assertFalse(banners.contains(notStarted));
        assertFalse(banners.contains(ended));
        assertTrue(banners.contains(running));
    }

    @Test
    void refreshTokenIsAlsoSentAsAnHttpOnlyCookie() throws Exception {
        String email = unique("admin") + "@example.com";
        authRepository.insertAdmin(UUID.randomUUID(), "Admin", email, passwordEncoder.encode("secret-123"), "super_admin");
        error(jsonBody(post("/api/admin/auth/login"), Map.of("email", email, "password", "wrong")), 400, "AUTH_INVALID_CREDENTIALS");

        MvcResult login = mvc.perform(jsonBody(post("/api/admin/auth/login"), Map.of("email", email, "password", "secret-123")))
                .andExpect(status().isOk()).andReturn();
        String loginToken = body(login).get("data").get("refreshToken").asText();
        String cookie = login.getResponse().getHeader(HttpHeaders.SET_COOKIE);
        assertTrue(cookie.startsWith(RefreshTokenCookie.NAME + "=" + loginToken + ";"), cookie);
        for (String attribute : List.of("Path=/api/admin/auth", "Max-Age=2592000", "Secure", "HttpOnly", "SameSite=Strict")) {
            assertTrue(cookie.contains("; " + attribute), attribute + " missing: " + cookie);
        }

        // Cookie only, no body: the flow for clients that no longer keep the token.
        MvcResult refreshed = mvc.perform(post("/api/admin/auth/refresh").cookie(new Cookie(RefreshTokenCookie.NAME, loginToken)))
                .andExpect(status().isOk()).andReturn();
        String rotated = body(refreshed).get("data").get("refreshToken").asText();
        assertNotEquals(loginToken, rotated);
        assertFalse(body(refreshed).get("data").get("accessToken").asText().isBlank());
        assertTrue(refreshed.getResponse().getHeader(HttpHeaders.SET_COOKIE).startsWith(RefreshTokenCookie.NAME + "=" + rotated + ";"));
        error(post("/api/admin/auth/refresh").cookie(new Cookie(RefreshTokenCookie.NAME, loginToken)), 400, "AUTH_REFRESH_TOKEN_INVALID");

        // Token in the body: the current frontend flow keeps working.
        MvcResult viaBody = mvc.perform(jsonBody(post("/api/admin/auth/refresh"), Map.of("refreshToken", rotated)))
                .andExpect(status().isOk()).andReturn();
        String latest = body(viaBody).get("data").get("refreshToken").asText();
        error(post("/api/admin/auth/refresh"), 400, "AUTH_REFRESH_TOKEN_INVALID");
        error(jsonBody(post("/api/admin/auth/refresh"), Map.of()), 400, "AUTH_REFRESH_TOKEN_INVALID");

        MockHttpServletResponse logout = mvc.perform(post("/api/admin/auth/logout").cookie(new Cookie(RefreshTokenCookie.NAME, latest)))
                .andExpect(status().isOk()).andReturn().getResponse();
        String cleared = logout.getHeader(HttpHeaders.SET_COOKIE);
        assertTrue(cleared.startsWith(RefreshTokenCookie.NAME + "=;"), cleared);
        assertTrue(cleared.contains("; Max-Age=0"), cleared);
        error(post("/api/admin/auth/refresh").cookie(new Cookie(RefreshTokenCookie.NAME, latest)), 400, "AUTH_REFRESH_TOKEN_INVALID");
        mvc.perform(post("/api/admin/auth/logout")).andExpect(status().isOk());
    }

    // ------------------------------------------------------------------ helpers

    private JsonNode error(MockHttpServletRequestBuilder request, int status, String code) throws Exception {
        JsonNode body = body(mvc.perform(request).andExpect(status().is(status)).andReturn());
        assertFalse(body.get("success").asBoolean(), body.toString());
        assertEquals(code, body.get("code").asText(), body.toString());
        assertFalse(body.get("message").asText().isBlank(), body.toString());
        assertTrue(body.get("data").isNull(), body.toString());
        return body;
    }

    private String coupon(Map<String, Object> fields) throws Exception {
        String code = unique("CP").toUpperCase();
        Map<String, Object> body = new HashMap<>(Map.of("code", code, "discountType", "PERCENT", "discountValue", 10));
        body.putAll(fields);
        createId("/api/admin/coupons", body);
        return code;
    }

    private JsonNode validateCoupon(String code, String subtotal) throws Exception {
        return body(mvc.perform(jsonBody(post("/api/coupons/validate"), Map.of("code", code, "subtotalAmount", subtotal)))
                .andExpect(status().isOk()).andReturn()).get("data");
    }

    private static void assertRejected(JsonNode result, String code) {
        assertFalse(result.get("valid").asBoolean(), result.toString());
        assertEquals(code, result.get("code").asText(), result.toString());
        assertFalse(result.get("message").asText().isBlank(), result.toString());
        assertEquals(0, BigDecimal.ZERO.compareTo(result.get("discountAmount").decimalValue()), result.toString());
        assertTrue(result.get("couponCode").isNull(), result.toString());
    }

    private String product(String categoryId, String name, Map<String, Object> fields) throws Exception {
        Map<String, Object> body = new HashMap<>(Map.of("categoryId", categoryId, "name", name, "status", "ACTIVE"));
        body.putAll(fields);
        String id = createId("/api/admin/products", body);
        Thread.sleep(5); // distinct createdAt, so newest-first order is deterministic
        return id;
    }

    private Map<String, Object> sku(String price, String salePrice, int stock) throws Exception {
        Map<String, Object> sku = new HashMap<>(Map.of("skuCode", unique("SKU"), "sizeId", idOf(getData("/api/admin/master-data").get("sizes"), "40"),
                "price", new BigDecimal(price), "stock", stock));
        if (salePrice != null) sku.put("salePrice", new BigDecimal(salePrice));
        return sku;
    }

    private Map<String, Object> variantSku(UUID variantClientId, String price, int stock, String status) throws Exception {
        Map<String, Object> sku = sku(price, null, stock);
        sku.put("variantClientId", variantClientId.toString());
        sku.put("status", status);
        return sku;
    }

    private static Map<String, Object> order(String skuId) {
        return Map.of("customerName", "Khách", "customerPhone", "0900000001", "shippingAddress", "HN",
                "items", List.of(Map.of("skuId", skuId, "quantity", 1)));
    }

    private List<String> ids(String url) throws Exception {
        List<String> ids = new ArrayList<>();
        getData(url).get("items").forEach(item -> ids.add(item.get("id").asText()));
        return ids;
    }

    private String createId(String url, Object payload) throws Exception {
        return body(mvc.perform(admin(jsonBody(post(url), payload))).andExpect(status().isOk()).andReturn()).get("data").get("id").asText();
    }

    private JsonNode getData(String url) throws Exception {
        return body(mvc.perform(admin(get(url))).andExpect(status().isOk()).andReturn()).get("data");
    }

    private static String idOf(JsonNode items, String value) {
        for (JsonNode item : items) {
            if (value.equals(item.get("value").asText())) return item.get("id").asText();
        }
        throw new AssertionError(value + " not found");
    }
}
