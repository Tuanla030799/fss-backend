package com.fss.backend.shared.ecommerce;

import com.fasterxml.jackson.databind.JsonNode;
import com.fss.backend.auth.CurrentAdmin;
import com.fss.backend.common.ApiException;
import com.fss.backend.file.FileAssetRepository;
import com.fss.backend.file.FileUrlService;
import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.util.Set;
import java.util.UUID;

@Component
public class EcommerceSupport {
    public static final String ACTIVE = "ACTIVE";
    public static final String INACTIVE = "INACTIVE";
    public static final String DRAFT = "DRAFT";
    public static final Set<String> PRODUCT_STATUSES = Set.of(ACTIVE, INACTIVE, DRAFT);
    public static final Set<String> PRODUCT_GENDERS = Set.of("MALE", "FEMALE", "UNISEX");
    public static final Set<String> ORDER_STATUSES = Set.of("PENDING", "CONFIRMED", "SHIPPING", "COMPLETED", "CANCELLED");

    private final CurrentAdmin currentAdmin;
    private final FileAssetRepository fileAssetRepository;
    private final FileUrlService fileUrlService;

    public EcommerceSupport(CurrentAdmin currentAdmin, FileAssetRepository fileAssetRepository, FileUrlService fileUrlService) {
        this.currentAdmin = currentAdmin;
        this.fileAssetRepository = fileAssetRepository;
        this.fileUrlService = fileUrlService;
    }

    public UUID adminId() {
        return currentAdmin.idOrNull();
    }

    public void activateFile(UUID fileId) {
        if (fileId == null) return;
        require(fileAssetRepository.findById(fileId) != null, "File not found: " + fileId);
        fileAssetRepository.updateStatus(fileId, ACTIVE, adminId());
    }

    public String publicUrl(String path) {
        return fileUrlService.publicUrl(path);
    }

    public String uniqueSlug(String slug, String name) {
        return slugify(def(slug, name));
    }

    public String slugify(String input) {
        String s = Normalizer.normalize(input == null ? UUID.randomUUID().toString() : input.trim().toLowerCase(), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-|-$)", "");
        return s.isBlank() ? UUID.randomUUID().toString() : s;
    }

    public String normalizeStatusNullable(String status) {
        if (status == null || status.isBlank()) return null;
        String normalized = status.trim().toUpperCase();
        require(Set.of(ACTIVE, INACTIVE).contains(normalized), "Status must be ACTIVE or INACTIVE");
        return normalized;
    }

    public String normalizeStatusDefault(String status, String fallback) {
        String normalized = normalizeStatusNullable(status);
        return normalized == null ? fallback : normalized;
    }

    public String normalizeProductStatusNullable(String status) {
        if (status == null || status.isBlank()) return null;
        String normalized = status.trim().toUpperCase();
        require(PRODUCT_STATUSES.contains(normalized), "Product status must be ACTIVE, INACTIVE or DRAFT");
        return normalized;
    }

    public String normalizeProductStatusDefault(String status) {
        String normalized = normalizeProductStatusNullable(status);
        return normalized == null ? DRAFT : normalized;
    }

    public String normalizeProductStatusRequired(String status) {
        String normalized = normalizeProductStatusNullable(status);
        require(normalized != null, "Status is required");
        return normalized;
    }

    public String normalizeProductGenderNullable(String gender) {
        if (gender == null || gender.isBlank()) return null;
        String normalized = gender.trim().toUpperCase();
        require(PRODUCT_GENDERS.contains(normalized), "Product gender must be MALE, FEMALE or UNISEX");
        return normalized;
    }

    public String normalizeProductGenderDefault(String gender) {
        String normalized = normalizeProductGenderNullable(gender);
        return normalized == null ? "UNISEX" : normalized;
    }

    public String normalizeOrderStatusNullable(String status) {
        if (status == null || status.isBlank()) return null;
        String normalized = status.trim().toUpperCase();
        require(ORDER_STATUSES.contains(normalized), "Order status is invalid");
        return normalized;
    }

    public String normalizeOrderStatusRequired(String status) {
        String normalized = normalizeOrderStatusNullable(status);
        require(normalized != null, "Order status is required");
        return normalized;
    }

    public String discountType(String value) {
        String normalized = value.trim().toUpperCase();
        require(Set.of("PERCENT", "FIXED").contains(normalized), "discountType must be PERCENT or FIXED");
        return normalized;
    }

    public int safeLimit(int limit) {
        return Math.min(Math.max(limit, 1), 100);
    }

    public int offset(int page, int limit) {
        return (Math.max(page, 1) - 1) * safeLimit(limit);
    }

    public Integer nz(Integer value) {
        return value == null ? 0 : value;
    }

    public Boolean bool(Boolean value) {
        return value != null && value;
    }

    public String def(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    public String trim(String value) {
        return value == null ? null : value.trim();
    }

    public String trimToNull(String value) {
        String trimmed = trim(value);
        return trimmed == null || trimmed.isBlank() ? null : trimmed;
    }

    public String trimRequired(String value, String field) {
        String trimmed = trimToNull(value);
        require(trimmed != null, field + " is required");
        return trimmed;
    }

    public String json(String value) {
        return value == null || value.isBlank() ? "{}" : value;
    }

    public String json(JsonNode value) {
        if (value == null || value.isNull()) {
            return "{}";
        }
        if (value.isTextual()) {
            String text = value.asText();
            return text == null || text.isBlank() ? "{}" : text;
        }
        return value.toString();
    }

    public void require(boolean ok, String message) {
        if (!ok) throw new ApiException(message);
    }
}
