package com.fss.backend.masterdata;

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
public class SizeColorController {
    private final SizeColorService service;

    public SizeColorController(SizeColorService service) {
        this.service = service;
    }

    @GetMapping("/admin/sizes")
    public ApiResponse<PageResult<SizeOption>> adminSizes(@RequestParam(required = false) String status,
                                                          @RequestParam(required = false) String keyword,
                                                          @RequestParam(defaultValue = "1") @Min(1) int page,
                                                          @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit) {
        return ApiResponse.ok("OK", service.listSizes(status, keyword, page, limit));
    }

    @GetMapping("/admin/sizes/{id}")
    public ApiResponse<SizeOption> adminSizeDetail(@PathVariable UUID id) {
        return ApiResponse.ok("OK", service.getSize(id));
    }

    @PostMapping("/admin/sizes")
    public ApiResponse<Map<String, UUID>> createSize(@Valid @RequestBody SizeRequest body) {
        return ApiResponse.ok("Created", Map.of("id", service.createSize(body)));
    }

    @PutMapping("/admin/sizes/{id}")
    public ApiResponse<Void> updateSize(@PathVariable UUID id, @Valid @RequestBody SizeRequest body) {
        service.updateSize(id, body);
        return ApiResponse.ok("Updated", null);
    }

    @DeleteMapping("/admin/sizes/{id}")
    public ApiResponse<Void> deleteSize(@PathVariable UUID id) {
        service.deleteSize(id);
        return ApiResponse.ok("Deleted", null);
    }

    @GetMapping("/admin/colors")
    public ApiResponse<PageResult<ColorOption>> adminColors(@RequestParam(required = false) String status,
                                                            @RequestParam(required = false) String keyword,
                                                            @RequestParam(defaultValue = "1") @Min(1) int page,
                                                            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit) {
        return ApiResponse.ok("OK", service.listColors(status, keyword, page, limit));
    }

    @GetMapping("/admin/colors/{id}")
    public ApiResponse<ColorOption> adminColorDetail(@PathVariable UUID id) {
        return ApiResponse.ok("OK", service.getColor(id));
    }

    @PostMapping("/admin/colors")
    public ApiResponse<Map<String, UUID>> createColor(@Valid @RequestBody ColorRequest body) {
        return ApiResponse.ok("Created", Map.of("id", service.createColor(body)));
    }

    @PutMapping("/admin/colors/{id}")
    public ApiResponse<Void> updateColor(@PathVariable UUID id, @Valid @RequestBody ColorRequest body) {
        service.updateColor(id, body);
        return ApiResponse.ok("Updated", null);
    }

    @DeleteMapping("/admin/colors/{id}")
    public ApiResponse<Void> deleteColor(@PathVariable UUID id) {
        service.deleteColor(id);
        return ApiResponse.ok("Deleted", null);
    }
}
