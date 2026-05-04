package com.fss.backend.content.blog;

import com.fss.backend.common.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api")
public class BlogPostController {
    private final BlogPostService service;

    public BlogPostController(BlogPostService service) {
        this.service = service;
    }

    @GetMapping("/blogs")
    public ApiResponse<List<BlogPost>> publicPosts(@RequestParam(required = false) String keyword,
                                                   @RequestParam(defaultValue = "1") @Min(1) int page,
                                                   @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit) {
        return ApiResponse.ok("OK", service.listPublicPosts(keyword, page, limit));
    }

    @GetMapping("/blogs/{slug}")
    public ApiResponse<BlogPost> publicPostDetail(@PathVariable String slug) {
        return ApiResponse.ok("OK", service.getPublicPost(slug));
    }

    @GetMapping("/admin/blogs")
    public ApiResponse<List<BlogPost>> adminPosts(@RequestParam(required = false) String status,
                                                 @RequestParam(required = false) String keyword,
                                                 @RequestParam(defaultValue = "1") @Min(1) int page,
                                                 @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit) {
        return ApiResponse.ok("OK", service.listAdminPosts(status, keyword, page, limit));
    }

    @GetMapping("/admin/blogs/{id}")
    public ApiResponse<BlogPost> adminPostDetail(@PathVariable UUID id) {
        return ApiResponse.ok("OK", service.getAdminPost(id));
    }

    @PostMapping("/admin/blogs")
    public ApiResponse<Map<String, UUID>> createPost(@Valid @RequestBody BlogPostRequest body) {
        return ApiResponse.ok("Created", Map.of("id", service.createPost(body)));
    }

    @PutMapping("/admin/blogs/{id}")
    public ApiResponse<Void> updatePost(@PathVariable UUID id, @Valid @RequestBody BlogPostRequest body) {
        service.updatePost(id, body);
        return ApiResponse.ok("Updated", null);
    }

    @DeleteMapping("/admin/blogs/{id}")
    public ApiResponse<Void> deletePost(@PathVariable UUID id) {
        service.deletePost(id);
        return ApiResponse.ok("Deleted", null);
    }
}
