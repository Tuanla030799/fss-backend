package com.fss.backend.catalog.product;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** Product list filters; same semantics as the previous SQL WHERE clause. */
public record ProductFilter(boolean publicOnly, String status, List<UUID> categoryIds, String categorySlug,
                            List<UUID> brandIds, String brandSlug, List<String> genders, String keyword,
                            List<String> sizes, List<String> colors, BigDecimal minPrice, BigDecimal maxPrice,
                            boolean featuredOnly) {
    /** Every live product, or every publicly visible one when {@code publicOnly}. */
    public static ProductFilter none(boolean publicOnly) {
        return new ProductFilter(publicOnly, null, List.of(), null, List.of(), null, List.of(), null, List.of(), List.of(),
                null, null, false);
    }

    public static ProductFilter featured() {
        return new ProductFilter(true, null, List.of(), null, List.of(), null, List.of(), null, List.of(), List.of(),
                null, null, true);
    }
}
