package com.fss.backend.it;

import com.fasterxml.jackson.databind.JsonNode;
import com.fss.backend.catalog.sku.SkuDocument;
import com.fss.backend.promotion.coupon.CouponDocument;
import com.fss.backend.sales.order.OrderDocument;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class OrderTransactionIntegrationTest extends MongoIntegrationTest {

    @Test
    void concurrentOrdersNeverOversellStock() throws Exception {
        Fixture fixture = fixture(5, 100);
        int buyers = 20;
        List<Integer> statuses = runConcurrently(buyers, () -> mvc.perform(jsonBody(post("/api/orders"),
                order(List.of(item(fixture.skuA, 1)), null))).andReturn().getResponse().getStatus());

        assertEquals(5, statuses.stream().filter(s -> s == 200).count());
        assertTrue(statuses.stream().allMatch(s -> s == 200 || s == 400 || s == 409), statuses.toString());
        assertEquals(0, stock(fixture.skuA));
        assertEquals(5, ordersFor(fixture.skuA));
    }

    @Test
    void failedSecondItemRollsBackStockOrderAndCoupon() throws Exception {
        Fixture fixture = fixture(10, 1);
        String code = createCoupon(5);

        mvc.perform(jsonBody(post("/api/orders"), order(List.of(item(fixture.skuA, 2), item(fixture.skuB, 2)), code)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Not enough stock for SKU: " + fixture.skuBCode));

        assertEquals(10, stock(fixture.skuA), "first SKU decrement must be rolled back");
        assertEquals(1, stock(fixture.skuB));
        assertEquals(0, ordersFor(fixture.skuA));
        assertEquals(0, usedCount(code));
    }

    @Test
    void couponUsageLimitHoldsUnderConcurrency() throws Exception {
        Fixture fixture = fixture(100, 100);
        String code = createCoupon(3);

        List<Integer> statuses = runConcurrently(12, () -> mvc.perform(jsonBody(post("/api/orders"),
                order(List.of(item(fixture.skuA, 1)), code))).andReturn().getResponse().getStatus());

        assertEquals(3, statuses.stream().filter(s -> s == 200).count(), statuses.toString());
        assertTrue(statuses.stream().allMatch(s -> s == 200 || s == 400 || s == 409), statuses.toString());
        assertEquals(3, usedCount(code));
        assertEquals(97, stock(fixture.skuA), "orders rejected by the coupon limit must not keep their stock decrement");
    }

    @Test
    void orderContractTotalsAndStatusHistory() throws Exception {
        Fixture fixture = fixture(10, 10);
        String code = createCoupon(10);
        MvcResult result = mvc.perform(jsonBody(post("/api/orders"), Map.of(
                "customerName", "Nguyễn Văn A", "customerPhone", "0900000000", "shippingAddress", "HN",
                "couponCode", code.toLowerCase(), "shippingFee", "30000",
                "items", List.of(item(fixture.skuA, 3))))).andExpect(status().isOk()).andReturn();
        JsonNode data = body(result).get("data");
        JsonNode order = data.get("order");
        // unit price = sale price 99.99; subtotal 299.97; 10% coupon = 30.00; + 30000 shipping
        assertEquals("299.97", order.get("subtotalAmount").decimalValue().toPlainString());
        assertEquals("30.00", order.get("discountAmount").decimalValue().toPlainString());
        assertEquals("30000.00", order.get("shippingFee").decimalValue().toPlainString());
        assertEquals("30269.97", order.get("totalAmount").decimalValue().toPlainString());
        assertEquals("PENDING", order.get("status").asText());
        assertEquals("UNPAID", order.get("paymentStatus").asText());
        assertEquals("PENDING", order.get("shippingStatus").asText());
        assertTrue(order.get("orderCode").asText().startsWith("SHOE-"));
        JsonNode line = data.get("items").get(0);
        assertEquals(order.get("id").asText(), line.get("orderId").asText());
        assertEquals("99.99", line.get("unitPrice").decimalValue().toPlainString());
        assertEquals("299.97", line.get("lineTotal").decimalValue().toPlainString());
        assertEquals("40", line.get("size").asText());

        String id = order.get("id").asText();
        mvc.perform(admin(jsonBody(patch("/api/admin/orders/" + id + "/status"), Map.of("status", "confirmed", "note", "ok"))))
                .andExpect(status().isOk());
        mvc.perform(admin(jsonBody(patch("/api/admin/orders/" + id + "/payment"), Map.of("paymentStatus", "paid", "paidAt", "2026-10-01T10:00:00"))))
                .andExpect(status().isOk());
        JsonNode history = body(mvc.perform(admin(get("/api/admin/orders/" + id + "/status-history"))).andReturn()).get("data");
        assertEquals("CONFIRMED", history.get(0).get("newStatus").asText());
        assertEquals("PENDING", history.get(0).get("oldStatus").asText());
        assertEquals("PENDING", history.get(1).get("newStatus").asText());
        JsonNode detail = body(mvc.perform(admin(get("/api/admin/orders/" + id))).andReturn()).get("data").get("order");
        assertEquals("PAID", detail.get("paymentStatus").asText());
        // Local time without offset is interpreted in Asia/Ho_Chi_Minh and returned in UTC.
        assertEquals("2026-10-01T03:00:00Z", detail.get("paidAt").asText());
        JsonNode list = body(mvc.perform(admin(get("/api/admin/orders?keyword=" + order.get("orderCode").asText()))).andReturn()).get("data");
        assertEquals(1, list.get("total").asLong());
    }

    // ------------------------------------------------------------------ helpers

    private record Fixture(String skuA, String skuB, String skuBCode) {}

    private Fixture fixture(int stockA, int stockB) throws Exception {
        JsonNode master = body(mvc.perform(admin(get("/api/admin/master-data"))).andReturn()).get("data");
        String size = null;
        for (JsonNode s : master.get("sizes")) if ("40".equals(s.get("value").asText())) size = s.get("id").asText();
        String color = master.get("colors").get(0).get("id").asText();
        String category = createId("/api/admin/categories", Map.of("name", unique("Cat")));
        String skuACode = unique("A");
        String skuBCode = unique("B");
        UUID variant = UUID.randomUUID();
        String productId = createId("/api/admin/products", Map.of("categoryId", category, "name", unique("Shoe"), "status", "ACTIVE",
                "variants", List.of(Map.of("clientId", variant.toString(), "name", "V", "colorId", color)),
                "skus", List.of(
                        Map.of("variantClientId", variant.toString(), "skuCode", skuACode, "sizeId", size,
                                "price", new BigDecimal("120"), "salePrice", new BigDecimal("99.99"), "stock", stockA),
                        Map.of("variantClientId", variant.toString(), "skuCode", skuBCode, "sizeId", size,
                                "price", new BigDecimal("50"), "stock", stockB))));
        JsonNode detail = body(mvc.perform(admin(get("/api/admin/products/" + productId))).andReturn()).get("data");
        String a = null, b = null;
        for (JsonNode sku : detail.get("skus")) {
            if (sku.get("skuCode").asText().equals(skuACode)) a = sku.get("id").asText();
            if (sku.get("skuCode").asText().equals(skuBCode)) b = sku.get("id").asText();
        }
        return new Fixture(a, b, skuBCode);
    }

    private String createCoupon(int usageLimit) throws Exception {
        String code = unique("CP").toUpperCase();
        createId("/api/admin/coupons", Map.of("code", code, "discountType", "PERCENT", "discountValue", 10, "usageLimit", usageLimit));
        return code;
    }

    private String createId(String url, Object payload) throws Exception {
        return body(mvc.perform(admin(jsonBody(post(url), payload))).andExpect(status().isOk()).andReturn()).get("data").get("id").asText();
    }

    private static Map<String, Object> item(String skuId, int quantity) {
        return Map.of("skuId", skuId, "quantity", quantity);
    }

    private static Map<String, Object> order(List<Map<String, Object>> items, String coupon) {
        Map<String, Object> body = new java.util.HashMap<>(Map.of("customerName", "Buyer", "customerPhone", "0911111111",
                "shippingAddress", "HCM", "items", items));
        if (coupon != null) body.put("couponCode", coupon);
        return body;
    }

    private int stock(String skuId) {
        return mongo.findById(UUID.fromString(skuId), SkuDocument.class).stock();
    }

    private long ordersFor(String skuId) {
        return mongo.count(Query.query(Criteria.where("items.skuId").is(UUID.fromString(skuId))), OrderDocument.class);
    }

    private int usedCount(String code) {
        return mongo.findOne(Query.query(Criteria.where("code").is(code)), CouponDocument.class).usedCount();
    }

    private static <T> List<T> runConcurrently(int count, Callable<T> task) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(count);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<T>> futures = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            futures.add(pool.submit(() -> {
                start.await();
                return task.call();
            }));
        }
        start.countDown();
        List<T> results = new ArrayList<>();
        for (Future<T> future : futures) results.add(future.get(120, TimeUnit.SECONDS));
        pool.shutdown();
        return results;
    }
}
