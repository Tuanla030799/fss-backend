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
import org.springframework.data.mongodb.core.aggregation.AggregationOperation;
import org.springframework.data.mongodb.core.aggregation.GroupOperation;
import org.springframework.data.mongodb.core.query.Collation;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Date;
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
    private static final Sort NEWEST_FIRST = Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("_id"));
    /** Vietnamese alphabetical order ("Đ" after "D", accents after the base letter), numbers compared by value. */
    private static final Collation VIETNAMESE = Collation.of("vi").numericOrdering(true);

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

    /** One page of the filtered products; the whole filtered list is sorted before paging, ties go newest first. */
    public List<ProductSummary> listProducts(ProductFilter filter, ProductSort sort, int limit, int offset) {
        Criteria criteria = filterCriteria(filter);
        if (criteria == null) return List.of();
        List<ProductDocument> page = switch (sort) {
            case NEWEST -> mongo.find(Query.query(criteria).with(NEWEST_FIRST).skip(offset).limit(limit), ProductDocument.class);
            case NAME_ASC -> mongo.find(Query.query(criteria).with(Sort.by(Sort.Order.asc("name")).and(NEWEST_FIRST))
                    .collation(VIETNAMESE).skip(offset).limit(limit), ProductDocument.class);
            case FEATURED -> aggregatePage(criteria, featuredFirst(), limit, offset);
            case PRICE_ASC, PRICE_DESC ->
                    aggregatePage(criteria, byLowestPrice(sort == ProductSort.PRICE_ASC, filter.publicOnly()), limit, offset);
        };
        return summaries(page, filter.publicOnly());
    }

    public long countProducts(ProductFilter filter) {
        Criteria criteria = filterCriteria(filter);
        return criteria == null ? 0 : mongo.count(Query.query(criteria), ProductDocument.class);
    }

    /** Live products among {@code ids}, restricted to publicly visible ones when {@code publicOnly}. */
    public List<ProductDocument> findLiveByIds(Collection<UUID> ids, boolean publicOnly) {
        if (ids.isEmpty()) return List.of();
        Criteria criteria = filterCriteria(ProductFilter.none(publicOnly));
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
        return doc == null ? null : summaries(List.of(doc), false).get(0);
    }

    public ProductSummary findProductSummaryBySlug(String slug, boolean publicOnly) {
        // Same visibility rules as the list (product, category and brand ACTIVE and not deleted): a product that is
        // not listed cannot be opened by its slug either.
        Criteria criteria = new Criteria().andOperator(filterCriteria(ProductFilter.none(publicOnly)), Criteria.where("slug").is(slug));
        ProductDocument doc = mongo.findOne(Query.query(criteria), ProductDocument.class);
        return doc == null ? null : summaries(List.of(doc), publicOnly).get(0);
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

    /** Records a change made through a product's variant or SKU endpoints as a change of the product. */
    public void touchProduct(UUID id, UUID adminId) {
        mongo.updateFirst(Query.query(live().and("_id").is(id)), new Update().set("updatedBy", adminId)
                .set("updatedAt", Instant.now()), ProductDocument.class);
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

    /** The product's variants; in public only the ACTIVE ones. */
    public List<ProductVariant> listVariants(UUID productId, boolean publicOnly) {
        Criteria criteria = live().and("productId").is(productId);
        if (publicOnly) criteria = criteria.and("status").is(ACTIVE);
        Query query = Query.query(criteria)
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

    /** The product's SKUs; in public only the sellable ones (ACTIVE, without variant or with an ACTIVE variant). */
    public List<Sku> listSkus(UUID productId, boolean publicOnly) {
        Criteria criteria = live().and("productId").is(productId);
        if (publicOnly) {
            List<UUID> activeVariantIds = mongo.findDistinct(Query.query(live().and("productId").is(productId)
                    .and("status").is(ACTIVE)), "_id", VariantDocument.class, UUID.class);
            criteria = criteria.and("status").is(ACTIVE)
                    .orOperator(Criteria.where("variantId").is(null), Criteria.where("variantId").in(activeVariantIds));
        }
        List<SkuDocument> docs = mongo.find(Query.query(criteria), SkuDocument.class);
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

    /**
     * Builds summaries in the given order with joined category/brand data, SKU aggregates and primary image path. In
     * public the aggregates only count sellable SKUs (see {@link #skuStages}), so prices and stock match what can be
     * ordered.
     */
    public List<ProductSummary> summaries(List<ProductDocument> products, boolean publicOnly) {
        if (products.isEmpty()) return List.of();
        Set<UUID> productIds = products.stream().map(ProductDocument::id).collect(Collectors.toSet());
        Map<UUID, CategoryDocument> categoryById = categories.findAllByIds(products.stream().map(ProductDocument::categoryId)
                .filter(Objects::nonNull).collect(Collectors.toSet()));
        Map<UUID, BrandDocument> brandById = brands.findAllByIds(products.stream().map(ProductDocument::brandId)
                .filter(Objects::nonNull).collect(Collectors.toSet()));
        Map<UUID, Document> skuStats = skuStats(productIds, publicOnly);

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
                    primaryFileId, primaryPath, MongoValues.odt(product.createdAt()), lastUpdated(product, stats)));
        }
        return result;
    }

    /**
     * The latest change of the product or of one of its counted SKUs: SKU edits (price, stock, status) and orders
     * update the SKU, while images and variants are saved with the product or touch it ({@link #touchProduct}).
     */
    private static OffsetDateTime lastUpdated(ProductDocument product, Document stats) {
        Instant updatedAt = product.updatedAt() != null ? product.updatedAt() : product.createdAt();
        if (stats != null && stats.get("updatedAt") instanceof Date skuUpdatedAt
                && (updatedAt == null || skuUpdatedAt.toInstant().isAfter(updatedAt))) {
            updatedAt = skuUpdatedAt.toInstant();
        }
        return MongoValues.odt(updatedAt);
    }

    private Map<UUID, Document> skuStats(Collection<UUID> productIds, boolean publicOnly) {
        GroupOperation group = Aggregation.group("productId").min("price").as("minPrice")
                .min("effectivePrice").as("minSalePrice").sum("stock").as("totalStock").max("updatedAt").as("updatedAt");
        Map<UUID, Document> stats = new HashMap<>();
        for (Document row : aggregateSkus(Criteria.where("productId").in(productIds), publicOnly, group)) {
            stats.put((UUID) row.get("_id"), row);
        }
        return stats;
    }

    /** Products having a SKU that matches {@code criteria} and counts (see {@link #skuStages}). */
    private Set<UUID> productIdsWithSku(Criteria criteria, boolean publicOnly) {
        Set<UUID> ids = new LinkedHashSet<>();
        aggregateSkus(criteria, publicOnly, Aggregation.group("productId")).forEach(row -> ids.add((UUID) row.get("_id")));
        return ids;
    }

    private List<Document> aggregateSkus(Criteria criteria, boolean publicOnly, GroupOperation group) {
        List<AggregationOperation> stages = new ArrayList<>();
        stages.add(Aggregation.match(criteria));
        skuStages(publicOnly).forEach(stage -> stages.add(context -> stage));
        stages.add(group);
        return mongo.aggregate(Aggregation.newAggregation(stages), SkuDocument.COLLECTION, Document.class).getMappedResults();
    }

    /**
     * Stages over {@code skus} keeping the SKUs that count for a product: every live SKU for the admin; in public only
     * sellable ones, i.e. ACTIVE and either without variant or with a live ACTIVE variant (the same rule orders use).
     */
    private static List<Document> skuStages(boolean publicOnly) {
        if (!publicOnly) return List.of(new Document("$match", new Document("deleted", false)));
        Document activeVariant = new Document("$elemMatch", new Document("deleted", false).append("status", ACTIVE));
        return List.of(
                new Document("$match", new Document("deleted", false).append("status", ACTIVE)),
                new Document("$lookup", new Document("from", VariantDocument.COLLECTION).append("localField", "variantId")
                        .append("foreignField", "_id").append("as", "_variant")),
                new Document("$match", new Document("$or", List.of(new Document("variantId", null),
                        new Document("_variant", activeVariant)))));
    }

    // ---------------------------------------------------------------- sorting

    private List<ProductDocument> aggregatePage(Criteria criteria, List<Document> sortStages, int limit, int offset) {
        List<AggregationOperation> stages = new ArrayList<>();
        stages.add(Aggregation.match(criteria));
        sortStages.forEach(stage -> stages.add(context -> stage));
        stages.add(Aggregation.skip((long) offset));
        stages.add(Aggregation.limit(limit));
        // Temporary sort fields are not part of ProductDocument and are ignored when mapping the results.
        return mongo.aggregate(Aggregation.newAggregation(stages), ProductDocument.COLLECTION, ProductDocument.class)
                .getMappedResults();
    }

    /** Featured products first, by featuredOrder; a non-featured product's leftover featuredOrder is ignored. */
    private static List<Document> featuredFirst() {
        Document featured = new Document("$eq", List.of("$isFeatured", true));
        return List.of(
                new Document("$addFields", new Document("_featured", new Document("$cond", List.of(featured, 1, 0)))
                        .append("_featuredOrder", new Document("$cond", List.of(featured,
                                new Document("$ifNull", List.of("$featuredOrder", 0)), 0)))),
                new Document("$sort", new Document("_featured", -1).append("_featuredOrder", 1)
                        .append("createdAt", -1).append("_id", -1)));
    }

    /**
     * By the lowest selling price (sale price, else price) among the SKUs counted in the summary, i.e. its
     * {@code minSalePrice}. Products without such a SKU have no price and come last in both directions.
     */
    private static List<Document> byLowestPrice(boolean ascending, boolean publicOnly) {
        List<Document> skuPipeline = new ArrayList<>(skuStages(publicOnly));
        skuPipeline.add(new Document("$group", new Document("_id", null).append("price", new Document("$min", "$effectivePrice"))));
        return List.of(
                new Document("$lookup", new Document("from", SkuDocument.COLLECTION).append("localField", "_id")
                        .append("foreignField", "productId").append("pipeline", skuPipeline).append("as", "_prices")),
                new Document("$addFields", new Document("_hasPrice", new Document("$size", "$_prices"))
                        .append("_price", new Document("$min", "$_prices.price"))),
                new Document("$sort", new Document("_hasPrice", -1).append("_price", ascending ? 1 : -1)
                        .append("createdAt", -1).append("_id", -1)));
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

        // Size and price filters only look at the SKUs counted in the summary, colors at ACTIVE variants in public.
        Set<UUID> productIds = null;
        if (notEmpty(filter.sizes())) {
            List<UUID> sizeIds = sizeColors.findSizeIdsByValues(filter.sizes());
            productIds = intersect(productIds, sizeIds.isEmpty() ? Set.of()
                    : productIdsWithSku(Criteria.where("sizeId").in(sizeIds), filter.publicOnly()));
        }
        if (notEmpty(filter.colors())) {
            List<UUID> colorIds = sizeColors.findColorIdsMatching(filter.colors());
            List<Criteria> anyColor = new ArrayList<>();
            if (!colorIds.isEmpty()) anyColor.add(Criteria.where("colorId").in(colorIds));
            filter.colors().forEach(color -> anyColor.add(Criteria.where("name").regex(containsIgnoreCase(color))));
            Criteria variants = filter.publicOnly() ? live().and("status").is(ACTIVE) : live();
            productIds = intersect(productIds, distinctProductIds(VariantDocument.COLLECTION,
                    variants.andOperator(new Criteria().orOperator(anyColor))));
        }
        if (filter.minPrice() != null) {
            productIds = intersect(productIds, productIdsWithSku(
                    Criteria.where("effectivePrice").gte(MongoValues.decimal(filter.minPrice())), filter.publicOnly()));
        }
        if (filter.maxPrice() != null) {
            productIds = intersect(productIds, productIdsWithSku(
                    Criteria.where("effectivePrice").lte(MongoValues.decimal(filter.maxPrice())), filter.publicOnly()));
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
