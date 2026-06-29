package com.fss.backend.content.collection;

import com.fss.backend.common.ApiResponse;
import com.fss.backend.common.PageResult;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api")
public class ProductCollectionController {
    private final ProductCollectionService service;

    public ProductCollectionController(ProductCollectionService service) {
        this.service = service;
    }

    @GetMapping("/collections")
    public ApiResponse<PageResult<ProductCollection>> publicCollections(@RequestParam(required = false) String keyword,
                                                                        @RequestParam(defaultValue = "1") @Min(1) int page,
                                                                        @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit) {
        return ApiResponse.ok("OK", service.listPublicCollections(keyword, page, limit));
    }

    @GetMapping("/collections/{slug}")
    public ApiResponse<ProductCollectionDetail> publicCollectionDetail(@PathVariable String slug,
                                                                      @RequestParam(defaultValue = "1") @Min(1) int page,
                                                                      @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit) {
        return ApiResponse.ok("OK", service.getPublicCollection(slug, page, limit));
    }

    @GetMapping("/admin/collections")
    public ApiResponse<PageResult<ProductCollection>> adminCollections(@RequestParam(required = false) String status,
                                                                       @RequestParam(required = false) String keyword,
                                                                       @RequestParam(defaultValue = "1") @Min(1) int page,
                                                                       @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit) {
        return ApiResponse.ok("OK", service.listAdminCollections(status, keyword, page, limit));
    }

    @GetMapping("/admin/collections/{id}")
    public ApiResponse<ProductCollectionDetail> adminCollectionDetail(@PathVariable UUID id) {
        return ApiResponse.ok("OK", service.getAdminCollection(id));
    }

    @PostMapping("/admin/collections")
    public ApiResponse<Map<String, UUID>> createCollection(@Valid @RequestBody ProductCollectionRequest body) {
        return ApiResponse.ok("Created", Map.of("id", service.createCollection(body)));
    }

    @PutMapping("/admin/collections/{id}")
    public ApiResponse<Void> updateCollection(@PathVariable UUID id, @Valid @RequestBody ProductCollectionRequest body) {
        service.updateCollection(id, body);
        return ApiResponse.ok("Updated", null);
    }

    @DeleteMapping("/admin/collections/{id}")
    public ApiResponse<Void> deleteCollection(@PathVariable UUID id) {
        service.deleteCollection(id);
        return ApiResponse.ok("Deleted", null);
    }
}
