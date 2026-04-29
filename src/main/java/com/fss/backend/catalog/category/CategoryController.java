package com.fss.backend.catalog.category;

import com.fss.backend.common.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api")
public class CategoryController {
    private final CategoryService service;

    public CategoryController(CategoryService service) {
        this.service = service;
    }

    @GetMapping("/categories")
    public ApiResponse<List<Category>> publicCategories() {
        return ApiResponse.ok("OK", service.listPublicCategories());
    }

    @GetMapping("/admin/categories")
    public ApiResponse<List<Category>> adminCategories(@RequestParam(required = false) String status,
                                                       @RequestParam(required = false) String keyword) {
        return ApiResponse.ok("OK", service.listAdminCategories(status, keyword));
    }

    @PostMapping("/admin/categories")
    public ApiResponse<Map<String, UUID>> createCategory(@Valid @RequestBody CategoryRequest body) {
        return ApiResponse.ok("Created", Map.of("id", service.createCategory(body)));
    }

    @PutMapping("/admin/categories/{id}")
    public ApiResponse<Void> updateCategory(@PathVariable UUID id, @Valid @RequestBody CategoryRequest body) {
        service.updateCategory(id, body);
        return ApiResponse.ok("Updated", null);
    }

    @DeleteMapping("/admin/categories/{id}")
    public ApiResponse<Void> deleteCategory(@PathVariable UUID id) {
        service.deleteCategory(id);
        return ApiResponse.ok("Deleted", null);
    }
}
