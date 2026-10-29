package com.fss.backend.content.page;

import com.fss.backend.common.ApiResponse;
import com.fss.backend.common.PageResult;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class SitePageController {
    private final SitePageService service;

    public SitePageController(SitePageService service) {
        this.service = service;
    }

    @GetMapping("/pages/{key}")
    public ApiResponse<SitePage> publicPage(@PathVariable String key) {
        return ApiResponse.ok("OK", service.getPage(key));
    }

    @GetMapping("/admin/pages")
    public ApiResponse<PageResult<SitePage>> adminPages() {
        return ApiResponse.ok("OK", service.listPages());
    }

    @GetMapping("/admin/pages/{key}")
    public ApiResponse<SitePage> adminPage(@PathVariable String key) {
        return ApiResponse.ok("OK", service.getPage(key));
    }

    @PutMapping("/admin/pages/{key}")
    public ApiResponse<Void> updatePage(@PathVariable String key, @Valid @RequestBody SitePageRequest body) {
        service.updatePage(key, body);
        return ApiResponse.ok("Updated", null);
    }
}
