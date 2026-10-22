package com.fss.backend.catalog.product;

import com.fss.backend.common.ApiException;
import com.fss.backend.common.ErrorCode;

import java.util.Arrays;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * Orderings of the public product list ({@code GET /products?sort=}). Every ordering ends with newest first, so pages
 * are stable. Prices are the lowest selling price (sale price, else price) among the product's sellable SKUs.
 */
public enum ProductSort {
    /** Featured products first (by their featured order), then newest. */
    FEATURED,
    /** Newest first; used when {@code sort} is absent. */
    NEWEST,
    /** Cheapest first; products without a sellable SKU come last. */
    PRICE_ASC,
    /** Most expensive first; products without a sellable SKU come last. */
    PRICE_DESC,
    /** Name A to Z using Vietnamese collation. */
    NAME_ASC;

    private static final String ALLOWED = Arrays.stream(values()).map(sort -> sort.name().toLowerCase(Locale.ROOT))
            .collect(Collectors.joining(", "));

    /** Accepts {@code price_asc}, {@code price-asc} or {@code PRICE_ASC}; blank means {@link #NEWEST}. */
    public static ProductSort parse(String value) {
        if (value == null || value.isBlank()) return NEWEST;
        String normalized = value.trim().replace('-', '_').toUpperCase(Locale.ROOT);
        return Arrays.stream(values()).filter(sort -> sort.name().equals(normalized)).findFirst()
                .orElseThrow(() -> new ApiException(ErrorCode.VALIDATION_ERROR, "sort must be one of: " + ALLOWED));
    }
}
