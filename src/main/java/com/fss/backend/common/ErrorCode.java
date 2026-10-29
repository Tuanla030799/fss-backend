package com.fss.backend.common;

import org.springframework.http.HttpStatusCode;

/**
 * Stable, machine-readable error codes sent in the {@code code} field of every error response. Clients branch on (and
 * translate) the code; {@code message} is for humans and may change. Published codes are never renamed.
 */
public enum ErrorCode {
    // Generic
    BAD_REQUEST,
    VALIDATION_ERROR,
    INVALID_REQUEST_BODY,
    NOT_FOUND,
    METHOD_NOT_ALLOWED,
    NOT_ACCEPTABLE,
    UNSUPPORTED_MEDIA_TYPE,
    CONFLICT,
    CONCURRENT_UPDATE,
    DUPLICATE_VALUE,
    PAYLOAD_TOO_LARGE,
    TOO_MANY_REQUESTS,
    ACCESS_DENIED,
    SERVICE_UNAVAILABLE,
    INTERNAL_ERROR,

    // Authentication
    AUTH_REQUIRED,
    AUTH_TOKEN_INVALID,
    AUTH_INVALID_CREDENTIALS,
    AUTH_REFRESH_TOKEN_INVALID,
    AUTH_REGISTRATION_DISABLED,
    ADMIN_USER_NOT_FOUND,
    EMAIL_ALREADY_EXISTS,

    // Catalog
    PRODUCT_NOT_FOUND,
    VARIANT_NOT_FOUND,
    SKU_NOT_FOUND,
    CATEGORY_NOT_FOUND,
    BRAND_NOT_FOUND,
    SIZE_NOT_FOUND,
    SIZE_INACTIVE,
    DEFAULT_SIZE_NOT_CONFIGURED,
    COLOR_NOT_FOUND,
    COLOR_INACTIVE,
    SLUG_ALREADY_EXISTS,
    SKU_CODE_ALREADY_EXISTS,
    SIZE_ALREADY_EXISTS,
    COLOR_ALREADY_EXISTS,

    // Content
    BLOG_POST_NOT_FOUND,
    COLLECTION_NOT_FOUND,
    BANNER_NOT_FOUND,
    SITE_PAGE_NOT_FOUND,
    FAQ_NOT_FOUND,

    // Files
    FILE_NOT_FOUND,
    FILE_UNAVAILABLE,
    FILE_EMPTY,
    FILE_TYPE_NOT_SUPPORTED,
    FILE_TOO_LARGE,
    IMAGE_TOO_LARGE,
    IMAGE_CORRUPTED,
    STORAGE_UNAVAILABLE,
    SERVER_BUSY,

    // Customers & orders
    CUSTOMER_NOT_FOUND,
    PHONE_ALREADY_EXISTS,
    ORDER_NOT_FOUND,
    SKU_UNAVAILABLE,
    OUT_OF_STOCK,

    // Coupons
    COUPON_NOT_FOUND,
    COUPON_INVALID,
    COUPON_NOT_STARTED,
    COUPON_EXPIRED,
    COUPON_USAGE_LIMIT_REACHED,
    COUPON_MIN_SUBTOTAL,
    COUPON_CODE_ALREADY_EXISTS,

    // Admin tools
    LOG_STREAM_LIMIT_REACHED;

    /** Generic code for errors that only carry an HTTP status (framework exceptions, legacy call sites). */
    public static ErrorCode forStatus(HttpStatusCode status) {
        return switch (status.value()) {
            case 400 -> BAD_REQUEST;
            case 401 -> AUTH_REQUIRED;
            case 403 -> ACCESS_DENIED;
            case 404 -> NOT_FOUND;
            case 405 -> METHOD_NOT_ALLOWED;
            case 406 -> NOT_ACCEPTABLE;
            case 409 -> CONFLICT;
            case 413 -> PAYLOAD_TOO_LARGE;
            case 415 -> UNSUPPORTED_MEDIA_TYPE;
            case 429 -> TOO_MANY_REQUESTS;
            case 503 -> SERVICE_UNAVAILABLE;
            default -> status.is4xxClientError() ? BAD_REQUEST : INTERNAL_ERROR;
        };
    }
}
