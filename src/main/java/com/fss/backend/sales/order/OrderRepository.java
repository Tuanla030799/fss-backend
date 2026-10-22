package com.fss.backend.sales.order;

import com.fss.backend.catalog.brand.BrandDocument;
import com.fss.backend.catalog.category.CategoryDocument;
import com.fss.backend.catalog.product.ProductDocument;
import com.fss.backend.catalog.product.VariantDocument;
import com.fss.backend.catalog.sku.SkuDocument;
import com.fss.backend.catalog.sku.SkuForOrder;
import com.fss.backend.masterdata.SizeDocument;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

import static com.fss.backend.shared.mongo.MongoValues.containsIgnoreCase;
import static com.fss.backend.shared.mongo.MongoValues.hasText;
import static com.fss.backend.shared.mongo.MongoValues.instant;
import static com.fss.backend.shared.mongo.MongoValues.live;
import static com.fss.backend.shared.mongo.MongoValues.odt;

@Repository
public class OrderRepository {
    private final MongoTemplate mongo;

    public OrderRepository(MongoTemplate mongo) {
        this.mongo = mongo;
    }

    /**
     * The SKU with names for the order snapshot, or null unless the storefront sells it: the SKU, its variant (if
     * any), product, category and brand (if any) must all be live and ACTIVE.
     */
    public SkuForOrder findSkuForOrder(UUID id) {
        SkuDocument sku = findLiveActive(id, SkuDocument.class);
        if (sku == null) return null;
        ProductDocument product = findLiveActive(sku.productId(), ProductDocument.class);
        if (product == null || findLiveActive(product.categoryId(), CategoryDocument.class) == null) return null;
        if (product.brandId() != null && findLiveActive(product.brandId(), BrandDocument.class) == null) return null;
        VariantDocument variant = sku.variantId() == null ? null : findLiveActive(sku.variantId(), VariantDocument.class);
        if (sku.variantId() != null && variant == null) return null;
        SizeDocument size = sku.sizeId() == null ? null : mongo.findById(sku.sizeId(), SizeDocument.class);
        if (size == null) return null;
        return new SkuForOrder(sku.id(), sku.productId(), sku.variantId(), sku.skuCode(), sku.sizeId(), size.value(),
                sku.price(), sku.salePrice(), sku.stock(), sku.status(), product.name(), variant == null ? null : variant.name());
    }

    private <T> T findLiveActive(UUID id, Class<T> type) {
        return id == null ? null : mongo.findOne(Query.query(live().and("_id").is(id).and("status").is("ACTIVE")), type);
    }

    /**
     * Conditional atomic decrement: only matches while enough stock is left, so stock never becomes negative even
     * under concurrent orders. Returns the number of updated SKUs (0 or 1).
     */
    public int decrementStock(UUID skuId, int quantity) {
        return (int) mongo.updateFirst(
                Query.query(live().and("_id").is(skuId).and("status").is("ACTIVE").and("stock").gte(quantity)),
                new Update().inc("stock", -quantity).set("updatedAt", Instant.now()), SkuDocument.class).getModifiedCount();
    }

    public void insertOrder(OrderDocument order) {
        mongo.insert(order);
    }

    public List<Order> listOrders(String status, String keyword, int limit, int offset) {
        Query query = Query.query(filter(status, keyword)).with(Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("_id")))
                .skip(offset).limit(limit);
        query.fields().exclude("items").exclude("statusHistory");
        return mongo.find(query, OrderDocument.class).stream().map(OrderRepository::toOrder).toList();
    }

    public long countOrders(String status, String keyword) {
        return mongo.count(Query.query(filter(status, keyword)), OrderDocument.class);
    }

    public OrderDocument findOrderDocument(UUID id) {
        return mongo.findOne(Query.query(live().and("_id").is(id)), OrderDocument.class);
    }

    public Order findOrderById(UUID id) {
        OrderDocument doc = findOrderDocument(id);
        return doc == null ? null : toOrder(doc);
    }

    /** Sets the status and appends the history entry in one atomic update. */
    public void updateOrderStatus(UUID id, String oldStatus, String status, String note, UUID adminId) {
        Instant now = Instant.now();
        mongo.updateFirst(Query.query(live().and("_id").is(id)), new Update().set("status", status).set("updatedAt", now)
                .push("statusHistory", new OrderDocument.History(UUID.randomUUID(), oldStatus, status, note, adminId, now)),
                OrderDocument.class);
    }

    public void updatePayment(UUID id, String paymentMethod, String paymentStatus, OffsetDateTime paidAt) {
        mongo.updateFirst(Query.query(live().and("_id").is(id)), new Update().set("paymentMethod", paymentMethod)
                .set("paymentStatus", paymentStatus).set("paidAt", instant(paidAt)).set("updatedAt", Instant.now()),
                OrderDocument.class);
    }

    public void updateShipping(UUID id, String shippingMethod, String shippingStatus, String trackingCode) {
        mongo.updateFirst(Query.query(live().and("_id").is(id)), new Update().set("shippingMethod", shippingMethod)
                .set("shippingStatus", shippingStatus).set("trackingCode", trackingCode).set("updatedAt", Instant.now()),
                OrderDocument.class);
    }

    public static List<OrderItem> items(OrderDocument doc) {
        if (doc.items() == null) return List.of();
        return doc.items().stream().map(item -> new OrderItem(item.id(), doc.id(), item.skuId(), item.productName(),
                item.skuCode(), item.variantName(), item.size(), item.unitPrice(), item.quantity(), item.lineTotal())).toList();
    }

    /** Newest first, like {@code ORDER BY created_at DESC}. */
    public static List<OrderStatusHistory> history(OrderDocument doc) {
        if (doc.statusHistory() == null) return List.of();
        List<OrderDocument.History> entries = doc.statusHistory();
        return java.util.stream.IntStream.range(0, entries.size()).boxed()
                .sorted(Comparator.comparing((Integer i) -> entries.get(i).createdAt(), Comparator.nullsFirst(Comparator.naturalOrder()))
                        .thenComparing(i -> i).reversed())
                .map(entries::get)
                .map(entry -> new OrderStatusHistory(entry.id(), doc.id(), entry.oldStatus(), entry.newStatus(), entry.note(),
                        entry.createdBy(), odt(entry.createdAt())))
                .toList();
    }

    static Order toOrder(OrderDocument doc) {
        return new Order(doc.id(), doc.orderCode(), doc.customerName(), doc.customerPhone(), doc.customerEmail(),
                doc.shippingAddress(), doc.note(), doc.status(), doc.couponId(), doc.customerId(), doc.paymentMethod(),
                doc.paymentStatus(), odt(doc.paidAt()), doc.shippingMethod(), doc.shippingStatus(), doc.trackingCode(),
                doc.internalNote(), doc.subtotalAmount(), doc.discountAmount(), doc.shippingFee(), doc.totalAmount(),
                odt(doc.createdAt()));
    }

    private Criteria filter(String status, String keyword) {
        Criteria criteria = live();
        if (hasText(status)) criteria = criteria.and("status").is(status);
        if (hasText(keyword)) {
            Pattern pattern = containsIgnoreCase(keyword);
            criteria = criteria.orOperator(Criteria.where("orderCode").regex(pattern), Criteria.where("customerName").regex(pattern),
                    Criteria.where("customerPhone").regex(pattern));
        }
        return criteria;
    }
}
