package com.fss.backend.content.page;

import com.fss.backend.common.ErrorCode;
import com.fss.backend.common.PageResult;
import com.fss.backend.content.html.HtmlContentImageUsageService;
import com.fss.backend.content.html.HtmlSanitizerService;
import com.fss.backend.file.FileReferenceService;
import com.fss.backend.shared.ecommerce.EcommerceSupport;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;

@Service
public class SitePageService {
    private final SitePageRepository repository;
    private final EcommerceSupport support;
    private final HtmlSanitizerService htmlSanitizerService;
    private final HtmlContentImageUsageService htmlContentImageUsageService;
    private final FileReferenceService fileReferenceService;

    public SitePageService(SitePageRepository repository, EcommerceSupport support,
                           HtmlSanitizerService htmlSanitizerService,
                           HtmlContentImageUsageService htmlContentImageUsageService,
                           FileReferenceService fileReferenceService) {
        this.repository = repository;
        this.support = support;
        this.htmlSanitizerService = htmlSanitizerService;
        this.htmlContentImageUsageService = htmlContentImageUsageService;
        this.fileReferenceService = fileReferenceService;
    }

    public SitePage getPage(String key) {
        return pageOrDefault(requireKey(key));
    }

    public PageResult<SitePage> listPages() {
        List<SitePage> items = Arrays.stream(SitePageKey.values()).map(this::pageOrDefault).toList();
        return support.pageResult(items, 1, items.size(), items.size());
    }

    @Transactional
    public void updatePage(String key, SitePageRequest request) {
        SitePageKey pageKey = requireKey(key);
        SitePage existing = repository.findPage(pageKey);
        String html = htmlSanitizerService.sanitize(request.contentHtml());
        repository.upsertPage(pageKey, request.title().trim(), blankToNull(request.summary()), html,
                htmlContentImageUsageService.filePathList(html), support.adminId());
        htmlContentImageUsageService.activateImages(html);
        if (existing != null) {
            fileReferenceService.releaseRemovedHtmlImages(existing.contentHtml(), html);
        }
    }

    private SitePageKey requireKey(String key) {
        SitePageKey pageKey = SitePageKey.fromKey(key);
        support.requireFound(pageKey != null, ErrorCode.SITE_PAGE_NOT_FOUND, "Site page not found");
        return pageKey;
    }

    /** A page that has never been saved is returned with its default title and no content. */
    private SitePage pageOrDefault(SitePageKey key) {
        SitePage page = repository.findPage(key);
        if (page == null) {
            return new SitePage(key.key(), key.defaultTitle(), null, null, null, null);
        }
        return new SitePage(page.key(), page.title(), page.summary(),
                htmlContentImageUsageService.rewriteForOutput(page.contentHtml()), page.createdAt(), page.updatedAt());
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
