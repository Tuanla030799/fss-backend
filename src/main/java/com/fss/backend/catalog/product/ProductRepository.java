package com.fss.backend.catalog.product;

import com.fss.backend.catalog.brand.BrandDocument;
import com.fss.backend.catalog.brand.BrandRepository;
import com.fss.backend.catalog.category.CategoryDocument;
import com.fss.backend.catalog.category.CategoryRepository;
import com.fss.backend.catalog.sku.Sku;
import com.fss.backend.catalog.sku.SkuDocument;
import com.fss.backend.file.FileAssetRepository;
import com.fss.backend.masterdata.ColorDocument;
import com.fss.backend.masterdata.SizeColorRepository;
import com.fss.backend.masterdata.SizeDocument;
import com.fss.backend.shared.mongo.MongoValues;
import org.bson.Document;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static com.fss.backend.shared.mongo.MongoValues.containsIgnoreCase;
import static com.fss.backend.shared.mongo.MongoValues.hasText;
import static com.fss.backend.shared.mongo.MongoValues.live;

/**
 * Product persistence. The previous SQL joined categories/brands/skus/images in one statement; here filters are
 * resolved to id sets with indexed queries first, the page is read from {@code products}, and the joined fields
 * (category/brand names, price/stock aggregates, primary image) are filled in with batched lookups for that page only.
 * Returned image fields contain stored file paths; the service turns them into public URLs.
 */
@Repository
public class ProductRepository {
    private static final String ACTIVE = "ACTIVE";

    private final MongoTemplate mongo;
    private final CategoryRepository categories;
    private final BrandRepository brands;
    private final SizeColorRepository sizeColors;
    private final FileAssetRepository files;

    public ProductRepository(MongoTemplate mongo, CategoryRepository categories, BrandRepository brands,
                             SizeColorRepository sizeColors, FileAssetRepository files) {
        this.mongo = mongo;
        this.categories = categories;
        this.brands = brands;
        this.sizeColors = sizeColors;
        this.files = files;
    }

    // ---------------------------------------------------------------- products

    public List<ProductSummary> listProducts(ProductFilter filter, int limit, int offset) {
        Criteria criteria = filterCriteria(filter);
        if (criteria == null) return List.of();
        Sort sort = filter.featuredOnly()
                ? Sort.by(Sort.Order.asc("featuredOrder"), Sort.Order.desc("createdAt"), Sort.Order.desc("_id"))
                : Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("_id"));
        List<ProductDocument> page = mongo.find(Query.query(criteria).with(sort).skip(offset).limit(limit), ProductDocument.class);
        return summaries(page);
    }

    public long countProducts(ProductFilter filter) {
        Criteria criteria = filterCriteria(filter);
        return criteria == null ? 0 : mongo.count(Query.query(criteria), ProductDocument.class);
    }

    /** Live products among {@code ids}, restricted to publicly visible ones when {@code publicOnly}. */
    public List<ProductDocument> findLiveByIds(Collection<UUID> ids, boolean publicOnly) {
        if (ids.isEmpty()) return List.of();
        ProductFilter filter = new ProductFilter(publicOnly, null, List.of(), null, List.of(), null, List.of(), null,
                List.of(), List.of(), null, null, false);
        Criteria criteria = filterCriteria(filter);
        if (criteria == null) return List.of();
        return mongo.find(Query.query(new Criteria().andOperator(criteria, Criteria.where("_id").in(ids))), ProductDocument.class);
    }

    public ProductDocument findLive(UUID id) {
        return mongo.findOne(Query.query(live().and("_id").is(id)), ProductDocument.class);
    }

    public boolean existsLive(UUID id) {
        return mongo.exists(Query.query(live().and("_id").is(id)), ProductDocument.class);
    }

    public ProductSummary findProductSummaryById(UUID id) {
        ProductDocument doc = findLive(id);
        return doc == null ? null : summaries(List.of(doc)).get(0);
    }

    public ProductSummary findProductSummaryBySlug(String slug, boolean publicOnly) {
        ProductDocument doc = mongo.findOne(Query.query(live().and("slug").is(slug)), ProductDocument.class);
        if (doc == null) return null;
        if (publicOnly) {
            if (!ACTIVE.equals(doc.status())) return null;
            CategoryDocument category = categories.findAllByIds(List.of(doc.categoryId())).get(doc.categoryId());
            if (category == null || !ACTIVE.equals(category.status())) return null;
            if (doc.brandId() != null) {
                BrandDocument brand = brands.findAllByIds(List.of(doc.brandId())).get(doc.brandId());
                if (brand != null && (!ACTIVE.equals(brand.status()) || brand.deleted())) return null;
            }
        }
        return summaries(List.of(doc)).get(0);
    }

    public String findProductDescriptionHtml(UUID id) {
        ProductDocument doc = findLive(id);
        return doc == null ? null : doc.descriptionHtml();
    }

    public boolean slugExists(String slug, UUID excludeId) {
        Criteria criteria = live().and("slug").is(slug);
        if (excludeId != null) criteria = criteria.and("_id").ne(excludeId);
        return mongo.exists(Query.query(criteria), ProductDocument.class);
    }

    public void insertProduct(UUID id, UUID categoryId, UUID brandId, String gender, String name, String slug,
                              String shortDescription, String descriptionHtml, List<String> htmlFilePaths, String status,
                              Boolean isFeatured, Integer featuredOrder, UUID adminId) {
        Instant now = Instant.now();
        mongo.insert(new ProductDocument(id, categoryId, brandId, gender, name, slug, shortDescription, descriptionHtml,
                htmlFilePaths, status, isFeatured, featuredOrder, List.of(), adminId, adminId, now, now, false, null));
    }

    public void updateProduct(UUID id, UUID categoryId, UUID brandId, String gender, String name, String slug,
                              String shortDescription, String descriptionHtml, List<String> htmlFilePaths, String status,
                              Boolean isFeatured, Integer featuredOrder, UUID adminId) {
        mongo.updateFirst(Query.query(live().and("_id").is(id)), new Update().set("categoryId", categoryId)
                .set("brandId", brandId).set("gender", gender).set("name", name).set("slug", slug)
                .set("shortDescription", shortDescription).set("descriptionHtml", descriptionHtml)
                .set("htmlFilePaths", htmlFilePaths).set("status", status).set("isFeatured", isFeatured)
                .set("featuredOrder", featuredOrder).set("updatedBy", adminId).set("updatedAt", Instant.now()),
                ProductDocument.class);
    }

    public void updateProductStatus(UUID id, String status, UUID adminId) {
        mongo.updateFirst(Query.query(live().and("_id").is(id)), new Update().set("status", status)
                .set("updatedBy", adminId).set("updatedAt", Instant.now()), ProductDocument.class);
    }

    public void softDeleteProduct(UUID id, UUID adminId) {
        Instant now = Instant.now();
        mongo.updateFirst(Query.query(live().and("_id").is(id)), new Update().set("deleted", true).set("deletedAt", now)
                .set("updatedBy", adminId).set("updatedAt", now), ProductDocument.class);
    }

    // ---------------------------------------------------------------- images (embedded)

    /** Images with {@code imageUrl} = stored file path, sorted like the old query (sortOrder, createdAt). */
    public List<ProductImage> listImages(UUID productId) {
        Query query = Query.query(Criteria.where("_id").is(productId));
        query.fields().include("images").include("deleted");
        ProductDocument doc = mongo.findOne(query, ProductDocument.class);
        if (doc == null || doc.images() == null) return List.of();
        Map<UUID, String> paths = files.findPathsByIds(doc.images().stream().map(ProductDocument.Image::fileId)
                .filter(Objects::nonNull).toList());
        return doc.images().stream()
                .sorted(Comparator.comparing((ProductDocument.Image image) -> image.sortOrder() == null ? 0 : image.sortOrder())
                        .thenComparing(image -> image.createdAt() == null ? Instant.EPOCH : image.createdAt()))
                .map(image -> new ProductImage(image.id(), productId, image.fileId(),
                        image.fileId() == null ? null : paths.get(image.fileId()), image.altText(), image.imageType(),
                        image.sortOrder(), image.isPrimary()))
                .toList();
    }

    public void replaceImages(UUID productId, List<ProductDocument.Image> images) {
        mongo.updateFirst(Query.query(Criteria.where("_id").is(productId)), Update.update("images", images), ProductDocument.class);
    }

    // ---------------------------------------------------------------- variants

    public List<ProductVariant> listVariants(UUID productId) {
        Query query = Query.query(live().and("productId").is(productId))
                .with(Sort.by(Sort.Order.asc("sortOrder"), Sort.Order.asc("createdAt"), Sort.Order.asc("_id")));
        return toVariants(mongo.find(query, VariantDocument.class));
    }

    public ProductVariant findVariantById(UUID id, UUID productId) {
        VariantDocument doc = mongo.findOne(Query.query(live().and("_id").is(id).and("productId").is(productId)), VariantDocument.class);
        return doc == null ? null : toVariants(List.of(doc)).stream().findFirst().orElse(null);
    }

    public void insertVariant(UUID id, UUID productId, String name, UUID colorId, UUID imageFileId, String status, Integer sortOrder) {
        Instant now = Instant.now();
        mongo.insert(new VariantDocument(id, productId, name, colorId, imageFileId, status, sortOrder, now, now, false, null));
    }

    public void updateVariant(UUID id, UUID productId, String name, UUID colorId, UUID imageFileId, String status, Integer sortOrder) {
        mongo.updateFirst(Query.query(live().and("_id").is(id).and("productId").is(productId)), new Update().set("name", name)
                .set("colorId", colorId).set("imageFileId", imageFileId).set("status", status).set("sortOrder", sortOrder)
                .set("updatedAt", Instant.now()), VariantDocument.class);
    }

    public void softDeleteVariant(UUID id, UUID productId) {
        softDeleteChildren(live().and("_id").is(id).and("productId").is(productId), VariantDocument.class);
    }

    public void softDeleteMissingVariants(UUID productId, Set<UUID> keepIds) {
        softDeleteChildren(live().and("productId").is(productId).and("_id").nin(keepIds), VariantDocument.class);
    }

    // ---------------------------------------------------------------- skus

    public List<Sku> listSkus(UUID productId) {
        List<SkuDocument> docs = mongo.find(Query.query(live().and("productId").is(productId)), SkuDocument.class);
        Map<UUID, SizeDocument> sizes = sizeColors.findSizesByIds(docs.stream().map(SkuDocument::sizeId).filter(Objects::nonNull).collect(Collectors.toSet()));
        return docs.stream()
                .map(doc -> toSku(doc, sizes.get(doc.sizeId())))
                .filter(Objects::nonNull)
                // Old query: ORDER BY sz.value ASC, s.created_at ASC (text ordering of the size value).
                .sorted(Comparator.comparing(Sku::size).thenComparing(sku -> sku.createdAt() == null ? Instant.EPOCH : sku.createdAt().toInstant()))
                .toList();
    }

    public Sku findSkuById(UUID id, UUID productId) {
        SkuDocument doc = mongo.findOne(Query.query(live().and("_id").is(id).and("productId").is(productId)), SkuDocument.class);
        if (doc == null) return null;
        return toSku(doc, sizeColors.findSizesByIds(List.of(doc.sizeId())).get(doc.sizeId()));
    }

    public boolean skuCodeExists(String skuCode, UUID excludeId) {
        Criteria criteria = live().and("skuCode").is(skuCode);
        if (excludeId != null) criteria = criteria.and("_id").ne(excludeId);
        return mongo.exists(Query.query(criteria), SkuDocument.class);
    }

    public void insertSku(UUID id, UUID productId, UUID variantId, String skuCode, UUID sizeId, BigDecimal price,
                          BigDecimal salePrice, Integer stock, String status) {
        Instant now = Instant.now();
        BigDecimal normalizedPrice = MongoValues.money(price);
        BigDecimal normalizedSale = MongoValues.money(salePrice);
        mongo.insert(new SkuDocument(id, productId, variantId, skuCode, sizeId, normalizedPrice, normalizedSale,
                normalizedSale != null ? normalizedSale : normalizedPrice, stock, status, now, now, false, null));
    }

    public void updateSku(UUID id, UUID productId, UUID variantId, String skuCode, UUID sizeId, BigDecimal price,
                          BigDecimal salePrice, Integer stock, String status) {
        BigDecimal normalizedPrice = MongoValues.money(price);
        BigDecimal normalizedSale = MongoValues.money(salePrice);
        mongo.updateFirst(Query.query(live().and("_id").is(id).and("productId").is(productId)), new Update()
                .set("variantId", variantId).set("skuCode", skuCode).set("sizeId", sizeId).set("price", normalizedPrice)
                .set("salePrice", normalizedSale).set("effectivePrice", normalizedSale != null ? normalizedSale : normalizedPrice)
                .set("stock", stock).set("status", status).set("updatedAt", Instant.now()), SkuDocument.class);
    }

    public void softDeleteSku(UUID id, UUID productId) {
        softDeleteChildren(live().and("_id").is(id).and("productId").is(productId), SkuDocument.class);
    }

    public void softDeleteMissingSkus(UUID productId, Set<UUID> keepIds) {
        softDeleteChildren(live().and("productId").is(productId).and("_id").nin(keepIds), SkuDocument.class);
    }

    // ---------------------------------------------------------------- summary building

    /** Builds summaries in the given order with joined category/brand data, SKU aggregates and primary image path. */
    public List<ProductSummary> summaries(List<ProductDocument> products) {
        if (products.isEmpty()) return List.of();
        Set<UUID> productIds = products.stream().map(ProductDocument::id).collect(Collectors.toSet());
        Map<UUID, CategoryDocument> categoryById = categories.findAllByIds(products.stream().map(ProductDocument::categoryId)
                .filter(Objects::nonNull).collect(Collectors.toSet()));
        Map<UUID, BrandDocument> brandById = brands.findAllByIds(products.stream().map(ProductDocument::brandId)
                .filter(Objects::nonNull).collect(Collectors.toSet()));
        Map<UUID, Document> skuStats = skuStats(productIds);

        Map<UUID, List<ProductDocument.Image>> orderedImages = new HashMap<>();
        Set<UUID> fileIds = new HashSet<>();
        for (ProductDocument product : products) {
            List<ProductDocument.Image> images = product.images() == null ? List.of() : product.images().stream()
                    .sorted(Comparator.comparing((ProductDocument.Image image) -> Boolean.TRUE.equals(image.isPrimary()) ? 0 : 1)
                            .thenComparing(image -> image.sortOrder() == null ? 0 : image.sortOrder()))
                    .toList();
            orderedImages.put(product.id(), images);
            images.stream().map(ProductDocument.Image::fileId).filter(Objects::nonNull).forEach(fileIds::add);
        }
        brandById.values().stream().map(BrandDocument::fileSizeId).filter(Objects::nonNull).forEach(fileIds::add);
        Map<UUID, String> paths = files.findPathsByIds(fileIds);

        List<ProductSummary> result = new ArrayList<>(products.size());
        for (ProductDocument product : products) {
            CategoryDocument category = categoryById.get(product.categoryId());
            BrandDocument brand = product.brandId() == null ? null : brandById.get(product.brandId());
            Document stats = skuStats.get(product.id());
            List<ProductDocument.Image> images = orderedImages.get(product.id());
            UUID primaryFileId = images.stream().map(ProductDocument.Image::fileId).filter(Objects::nonNull).findFirst().orElse(null);
            String primaryPath = images.stream().map(ProductDocument.Image::fileId).filter(Objects::nonNull)
                    .map(paths::get).filter(Objects::nonNull).findFirst().orElse(null);
            result.add(new ProductSummary(product.id(), product.categoryId(), category == null ? null : category.name(),
                    product.brandId(), brand == null ? null : brand.name(), brand == null ? null : brand.slug(),
                    brand == null || brand.fileSizeId() == null ? null : paths.get(brand.fileSizeId()),
                    product.gender(), product.name(), product.slug(), product.shortDescription(), product.status(),
                    product.isFeatured(), product.featuredOrder(),
                    stats == null ? null : MongoValues.bigDecimal(stats.get("minPrice")),
                    stats == null ? null : MongoValues.bigDecimal(stats.get("minSalePrice")),
                    stats == null ? 0 : ((Number) stats.getOrDefault("totalStock", 0)).intValue(),
                    primaryFileId, primaryPath, MongoValues.odt(product.createdAt())));
        }
        return result;
    }

    private Map<UUID, Document> skuStats(Collection<UUID> productIds) {
        Aggregation aggregation = Aggregation.newAggregation(
                Aggregation.match(live().and("productId").in(productIds)),
                Aggregation.group("productId").min("price").as("minPrice").min("effectivePrice").as("minSalePrice")
                        .sum("stock").as("totalStock"));
        Map<UUID, Document> stats = new HashMap<>();
        for (Document row : mongo.aggregate(aggregation, SkuDocument.COLLECTION, Document.class).getMappedResults()) {
            stats.put((UUID) row.get("_id"), row);
        }
        return stats;
    }

    // ---------------------------------------------------------------- filters

    /** Returns null when a filter can match nothing (e.g. unknown category slug), so callers skip the query. */
    Criteria filterCriteria(ProductFilter filter) {
        List<Criteria> and = new ArrayList<>();
        and.add(live());
        if (filter.publicOnly()) {
            and.add(Criteria.where("status").is(ACTIVE));
            and.add(Criteria.where("categoryId").in(categories.findActiveIds()));
            and.add(new Criteria().orOperator(Criteria.where("brandId").is(null), Criteria.where("brandId").in(brands.findActiveIds())));
        }
        if (hasText(filter.status())) and.add(Criteria.where("status").is(filter.status()));
        if (notEmpty(filter.categoryIds())) and.add(Criteria.where("categoryId").in(filter.categoryIds()));
        if (hasText(filter.categorySlug())) {
            List<UUID> ids = categories.findIdsBySlug(filter.categorySlug());
            if (ids.isEmpty()) return null;
            and.add(Criteria.where("categoryId").in(ids));
        }
        if (notEmpty(filter.brandIds())) and.add(Criteria.where("brandId").in(filter.brandIds()));
        if (hasText(filter.brandSlug())) {
            List<UUID> ids = brands.findIdsBySlug(filter.brandSlug());
            if (ids.isEmpty()) return null;
            and.add(Criteria.where("brandId").in(ids));
        }
        if (notEmpty(filter.genders())) and.add(Criteria.where("gender").in(filter.genders()));
        if (hasText(filter.keyword())) {
            Pattern pattern = containsIgnoreCase(filter.keyword());
            and.add(new Criteria().orOperator(Criteria.where("name").regex(pattern), Criteria.where("shortDescription").regex(pattern)));
        }
        if (filter.featuredOnly()) and.add(Criteria.where("isFeatured").is(true));

        Set<UUID> productIds = null;
        if (notEmpty(filter.sizes())) {
            List<UUID> sizeIds = sizeColors.findSizeIdsByValues(filter.sizes());
            productIds = intersect(productIds, sizeIds.isEmpty() ? Set.of()
                    : distinctProductIds(SkuDocument.COLLECTION, live().and("sizeId").in(sizeIds)));
        }
        if (notEmpty(filter.colors())) {
            List<UUID> colorIds = sizeColors.findColorIdsMatching(filter.colors());
            List<Criteria> anyColor = new ArrayList<>();
            if (!colorIds.isEmpty()) anyColor.add(Criteria.where("colorId").in(colorIds));
            filter.colors().forEach(color -> anyColor.add(Criteria.where("name").regex(containsIgnoreCase(color))));
            productIds = intersect(productIds, distinctProductIds(VariantDocument.COLLECTION,
                    live().andOperator(new Criteria().orOperator(anyColor))));
        }
        if (filter.minPrice() != null) {
            productIds = intersect(productIds, distinctProductIds(SkuDocument.COLLECTION,
                    live().and("effectivePrice").gte(MongoValues.decimal(filter.minPrice()))));
        }
        if (filter.maxPrice() != null) {
            productIds = intersect(productIds, distinctProductIds(SkuDocument.COLLECTION,
                    live().and("effectivePrice").lte(MongoValues.decimal(filter.maxPrice()))));
        }
        if (productIds != null) {
            if (productIds.isEmpty()) return null;
            and.add(Criteria.where("_id").in(productIds));
        }
        return new Criteria().andOperator(and);
    }

    private Set<UUID> distinctProductIds(String collection, Criteria criteria) {
        return new LinkedHashSet<>(mongo.findDistinct(Query.query(criteria), "productId", collection, UUID.class));
    }

    private static Set<UUID> intersect(Set<UUID> current, Set<UUID> next) {
        if (current == null) return new LinkedHashSet<>(next);
        current.retainAll(next);
        return current;
    }

    private static boolean notEmpty(Collection<?> values) {
        return values != null && !values.isEmpty();
    }

    // ---------------------------------------------------------------- mapping helpers

    private List<ProductVariant> toVariants(List<VariantDocument> docs) {
        Map<UUID, ColorDocument> colors = sizeColors.findColorsByIds(docs.stream().map(VariantDocument::colorId)
                .filter(Objects::nonNull).collect(Collectors.toSet()));
        Map<UUID, String> paths = files.findPathsByIds(docs.stream().map(VariantDocument::imageFileId)
                .filter(Objects::nonNull).toList());
        return docs.stream()
                // Old query used an inner JOIN on colors: variants without a color row were not returned.
                .filter(doc -> colors.containsKey(doc.colorId()))
                .map(doc -> {
                    ColorDocument color = colors.get(doc.colorId());
                    return new ProductVariant(doc.id(), doc.productId(), doc.name(), doc.colorId(), color.name(),
                            color.colorCode(), doc.imageFileId(), doc.imageFileId() == null ? null : paths.get(doc.imageFileId()),
                            doc.status(), doc.sortOrder());
                }).toList();
    }

    private static Sku toSku(SkuDocument doc, SizeDocument size) {
        // Old query used an inner JOIN on sizes.
        if (size == null) return null;
        return new Sku(doc.id(), doc.productId(), doc.variantId(), doc.skuCode(), doc.sizeId(), size.value(), doc.price(),
                doc.salePrice(), doc.stock(), doc.status(), MongoValues.odt(doc.createdAt()));
    }

    private void softDeleteChildren(Criteria criteria, Class<?> type) {
        Instant now = Instant.now();
        mongo.updateMulti(Query.query(criteria), new Update().set("deleted", true).set("deletedAt", now).set("updatedAt", now), type);
    }
}
