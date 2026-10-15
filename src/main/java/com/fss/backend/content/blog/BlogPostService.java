package com.fss.backend.content.blog;

import com.fss.backend.common.PageResult;
import com.fss.backend.content.html.HtmlContentImageUsageService;
import com.fss.backend.content.html.HtmlSanitizerService;
import com.fss.backend.file.FileReferenceService;
import com.fss.backend.shared.ecommerce.EcommerceSupport;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class BlogPostService {
    private static final Set<String> POST_STATUSES = Set.of(EcommerceSupport.ACTIVE, EcommerceSupport.INACTIVE, EcommerceSupport.DRAFT);

    private final BlogPostRepository repository;
    private final EcommerceSupport support;
    private final HtmlSanitizerService htmlSanitizerService;
    private final HtmlContentImageUsageService htmlContentImageUsageService;
    private final FileReferenceService fileReferenceService;

    public BlogPostService(BlogPostRepository repository, EcommerceSupport support,
                           HtmlSanitizerService htmlSanitizerService,
                           HtmlContentImageUsageService htmlContentImageUsageService,
                           FileReferenceService fileReferenceService) {
        this.repository = repository;
        this.support = support;
        this.htmlSanitizerService = htmlSanitizerService;
        this.htmlContentImageUsageService = htmlContentImageUsageService;
        this.fileReferenceService = fileReferenceService;
    }

    public PageResult<BlogPost> listPublicPosts(String keyword, int page, int limit) {
        int safeLimit = support.safeLimit(limit);
        List<BlogPost> items = repository.listPosts(true, null, keyword, safeLimit, support.offset(page, safeLimit))
                .stream().map(this::withCoverUrl).toList();
        return support.pageResult(items, page, safeLimit, repository.countPosts(true, null, keyword));
    }

    public BlogPost getPublicPost(String slug) {
        BlogPost post = repository.findPostBySlug(slug, true);
        support.require(post != null, "Blog post not found");
        return withCoverUrl(post);
    }

    public PageResult<BlogPost> listAdminPosts(String status, String keyword, int page, int limit) {
        String normalizedStatus = normalizePostStatusNullable(status);
        int safeLimit = support.safeLimit(limit);
        List<BlogPost> items = repository.listPosts(false, normalizedStatus, keyword, safeLimit, support.offset(page, safeLimit))
                .stream().map(this::withCoverUrl).toList();
        return support.pageResult(items, page, safeLimit, repository.countPosts(false, normalizedStatus, keyword));
    }

    public BlogPost getAdminPost(UUID id) {
        BlogPost post = repository.findPostById(id);
        support.require(post != null, "Blog post not found");
        return withCoverUrl(post);
    }

    @Transactional
    public UUID createPost(BlogPostRequest request) {
        UUID id = UUID.randomUUID();
        support.activateFile(request.coverFileId());
        String status = normalizePostStatusDefault(request.status());
        ContentPayload content = contentPayload(request);
        repository.insertPost(id, request.title().trim(), uniqueSlug(request.slug(), request.title(), null),
                request.excerpt(), content.html(), htmlContentImageUsageService.filePathList(content.html()),
                request.coverFileId(), status,
                normalizePublishedAt(status, request.publishedAt()), support.adminId());
        htmlContentImageUsageService.activateImages(content.html());
        return id;
    }

    @Transactional
    public void updatePost(UUID id, BlogPostRequest request) {
        BlogPost existing = repository.findPostById(id);
        support.require(existing != null, "Blog post not found");
        support.activateFile(request.coverFileId());
        String status = normalizePostStatusDefault(request.status());
        ContentPayload content = contentPayload(request);
        repository.updatePost(id, request.title().trim(), uniqueSlug(request.slug(), request.title(), id),
                request.excerpt(), content.html(), htmlContentImageUsageService.filePathList(content.html()),
                request.coverFileId(), status,
                normalizePublishedAt(status, request.publishedAt()), support.adminId());
        htmlContentImageUsageService.activateImages(content.html());
        fileReferenceService.releaseFile(existing.coverFileId());
        fileReferenceService.releaseRemovedHtmlImages(existing.contentHtml(), content.html());
    }

    @Transactional
    public void deletePost(UUID id) {
        BlogPost existing = repository.findPostById(id);
        support.require(existing != null, "Blog post not found");
        repository.softDeletePost(id, support.adminId());
        fileReferenceService.releaseFile(existing.coverFileId());
        fileReferenceService.releaseRemovedHtmlImages(existing.contentHtml(), null);
    }

    private OffsetDateTime normalizePublishedAt(String status, OffsetDateTime publishedAt) {
        if (publishedAt != null) return publishedAt;
        return EcommerceSupport.ACTIVE.equals(status) ? OffsetDateTime.now(ZoneOffset.UTC) : null;
    }

    private String normalizePostStatusNullable(String status) {
        if (status == null || status.isBlank()) return null;
        String normalized = status.trim().toUpperCase();
        support.require(POST_STATUSES.contains(normalized), "Blog status must be ACTIVE, INACTIVE or DRAFT");
        return normalized;
    }

    private String normalizePostStatusDefault(String status) {
        String normalized = normalizePostStatusNullable(status);
        return normalized == null ? EcommerceSupport.DRAFT : normalized;
    }

    private String uniqueSlug(String slug, String title, UUID exclude) {
        String normalized = support.uniqueSlug(slug, title);
        support.require(!repository.slugExists(normalized, exclude), "Blog slug already exists");
        return normalized;
    }

    private BlogPost withCoverUrl(BlogPost post) {
        return post == null ? null : new BlogPost(post.id(), post.title(), post.slug(),
                post.excerpt(), "{}", htmlContentImageUsageService.rewriteForOutput(post.contentHtml()), post.coverFileId(),
                support.publicUrl(post.coverImageUrl()), post.status(), post.publishedAt(), post.createdAt());
    }

    private ContentPayload contentPayload(BlogPostRequest request) {
        String html = request.contentHtml();
        if (html == null && looksLikeHtml(request.contentJson())) {
            html = request.contentJson();
        }
        return new ContentPayload(htmlSanitizerService.sanitize(html));
    }

    private boolean looksLikeHtml(String value) {
        return value != null && value.trim().startsWith("<");
    }

    private record ContentPayload(String html) {}
}
