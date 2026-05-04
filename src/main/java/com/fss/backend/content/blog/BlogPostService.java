package com.fss.backend.content.blog;

import com.fss.backend.shared.ecommerce.EcommerceSupport;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class BlogPostService {
    private static final Set<String> POST_STATUSES = Set.of(EcommerceSupport.ACTIVE, EcommerceSupport.INACTIVE, EcommerceSupport.DRAFT);

    private final BlogPostMapper mapper;
    private final EcommerceSupport support;

    public BlogPostService(BlogPostMapper mapper, EcommerceSupport support) {
        this.mapper = mapper;
        this.support = support;
    }

    public List<BlogPost> listPublicPosts(String keyword, int page, int limit) {
        return mapper.listPosts(true, null, keyword, support.safeLimit(limit), support.offset(page, limit))
                .stream().map(this::withCoverUrl).toList();
    }

    public BlogPost getPublicPost(String slug) {
        BlogPost post = mapper.findPostBySlug(slug, true);
        support.require(post != null, "Blog post not found");
        return withCoverUrl(post);
    }

    public List<BlogPost> listAdminPosts(String status, String keyword, int page, int limit) {
        return mapper.listPosts(false, normalizePostStatusNullable(status), keyword, support.safeLimit(limit), support.offset(page, limit))
                .stream().map(this::withCoverUrl).toList();
    }

    public BlogPost getAdminPost(UUID id) {
        BlogPost post = mapper.findPostById(id);
        support.require(post != null, "Blog post not found");
        return withCoverUrl(post);
    }

    @Transactional
    public UUID createPost(BlogPostRequest request) {
        UUID id = UUID.randomUUID();
        support.activateFile(request.coverFileId());
        String status = normalizePostStatusDefault(request.status());
        mapper.insertPost(id, request.title().trim(), uniqueSlug(request.slug(), request.title(), null),
                request.excerpt(), support.json(request.contentJson()), request.coverFileId(), status,
                normalizePublishedAt(status, request.publishedAt()), support.adminId());
        return id;
    }

    @Transactional
    public void updatePost(UUID id, BlogPostRequest request) {
        support.require(mapper.findPostById(id) != null, "Blog post not found");
        support.activateFile(request.coverFileId());
        String status = normalizePostStatusDefault(request.status());
        mapper.updatePost(id, request.title().trim(), uniqueSlug(request.slug(), request.title(), id),
                request.excerpt(), support.json(request.contentJson()), request.coverFileId(), status,
                normalizePublishedAt(status, request.publishedAt()), support.adminId());
    }

    @Transactional
    public void deletePost(UUID id) {
        support.require(mapper.findPostById(id) != null, "Blog post not found");
        mapper.softDeletePost(id, support.adminId());
    }

    private OffsetDateTime normalizePublishedAt(String status, OffsetDateTime publishedAt) {
        if (publishedAt != null) return publishedAt;
        return EcommerceSupport.ACTIVE.equals(status) ? OffsetDateTime.now() : null;
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
        support.require(mapper.countPostSlug(normalized, exclude) == 0, "Blog slug already exists");
        return normalized;
    }

    private BlogPost withCoverUrl(BlogPost post) {
        return post == null || post.coverImageUrl() == null ? post : new BlogPost(post.id(), post.title(), post.slug(),
                post.excerpt(), post.contentJson(), post.coverFileId(), support.publicUrl(post.coverImageUrl()),
                post.status(), post.publishedAt(), post.createdAt());
    }
}
