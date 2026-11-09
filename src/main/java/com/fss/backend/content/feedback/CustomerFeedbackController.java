package com.fss.backend.content.feedback;

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
public class CustomerFeedbackController {
    private final CustomerFeedbackService service;

    public CustomerFeedbackController(CustomerFeedbackService service) {
        this.service = service;
    }

    @GetMapping("/customer-feedbacks")
    public ApiResponse<PageResult<CustomerFeedback>> publicFeedbacks(@RequestParam(defaultValue = "1") @Min(1) int page,
                                                                     @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit) {
        return ApiResponse.ok("OK", service.listPublic(page, limit));
    }

    @GetMapping("/admin/customer-feedbacks")
    public ApiResponse<PageResult<CustomerFeedback>> adminFeedbacks(@RequestParam(required = false) String status,
                                                                    @RequestParam(defaultValue = "1") @Min(1) int page,
                                                                    @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit) {
        return ApiResponse.ok("OK", service.listAdmin(status, page, limit));
    }

    @GetMapping("/admin/customer-feedbacks/{id}")
    public ApiResponse<CustomerFeedback> adminFeedbackDetail(@PathVariable UUID id) {
        return ApiResponse.ok("OK", service.getAdmin(id));
    }

    @PostMapping("/admin/customer-feedbacks")
    public ApiResponse<Map<String, UUID>> createFeedback(@Valid @RequestBody CustomerFeedbackRequest body) {
        return ApiResponse.ok("Created", Map.of("id", service.create(body)));
    }

    @PutMapping("/admin/customer-feedbacks/{id}")
    public ApiResponse<Void> updateFeedback(@PathVariable UUID id, @Valid @RequestBody CustomerFeedbackRequest body) {
        service.update(id, body);
        return ApiResponse.ok("Updated", null);
    }

    @DeleteMapping("/admin/customer-feedbacks/{id}")
    public ApiResponse<Void> deleteFeedback(@PathVariable UUID id) {
        service.delete(id);
        return ApiResponse.ok("Deleted", null);
    }
}
