package com.fss.backend.content.faq;

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
public class FaqController {
    private final FaqService service;

    public FaqController(FaqService service) {
        this.service = service;
    }

    @GetMapping("/faqs")
    public ApiResponse<PageResult<Faq>> publicFaqs(@RequestParam(defaultValue = "1") @Min(1) int page,
                                                   @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit) {
        return ApiResponse.ok("OK", service.listPublicFaqs(page, limit));
    }

    @GetMapping("/admin/faqs")
    public ApiResponse<PageResult<Faq>> adminFaqs(@RequestParam(required = false) String status,
                                                  @RequestParam(required = false) String keyword,
                                                  @RequestParam(defaultValue = "1") @Min(1) int page,
                                                  @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit) {
        return ApiResponse.ok("OK", service.listAdminFaqs(status, keyword, page, limit));
    }

    @GetMapping("/admin/faqs/{id}")
    public ApiResponse<Faq> adminFaqDetail(@PathVariable UUID id) {
        return ApiResponse.ok("OK", service.getAdminFaq(id));
    }

    @PostMapping("/admin/faqs")
    public ApiResponse<Map<String, UUID>> createFaq(@Valid @RequestBody FaqRequest body) {
        return ApiResponse.ok("Created", Map.of("id", service.createFaq(body)));
    }

    @PutMapping("/admin/faqs/{id}")
    public ApiResponse<Void> updateFaq(@PathVariable UUID id, @Valid @RequestBody FaqRequest body) {
        service.updateFaq(id, body);
        return ApiResponse.ok("Updated", null);
    }

    @DeleteMapping("/admin/faqs/{id}")
    public ApiResponse<Void> deleteFaq(@PathVariable UUID id) {
        service.deleteFaq(id);
        return ApiResponse.ok("Deleted", null);
    }
}
