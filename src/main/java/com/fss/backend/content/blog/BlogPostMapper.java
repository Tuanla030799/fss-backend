package com.fss.backend.content.blog;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Mapper
public interface BlogPostMapper {
    List<BlogPost> listPosts(@Param("publicOnly") boolean publicOnly, @Param("status") String status,
                             @Param("keyword") String keyword, @Param("limit") int limit, @Param("offset") int offset);

    BlogPost findPostById(@Param("id") UUID id);

    BlogPost findPostBySlug(@Param("slug") String slug, @Param("publicOnly") boolean publicOnly);

    int countPostSlug(@Param("slug") String slug, @Param("excludeId") UUID excludeId);

    void insertPost(@Param("id") UUID id, @Param("title") String title, @Param("slug") String slug,
                    @Param("excerpt") String excerpt, @Param("contentJson") String contentJson,
                    @Param("contentHtml") String contentHtml,
                    @Param("coverFileId") UUID coverFileId, @Param("status") String status,
                    @Param("publishedAt") OffsetDateTime publishedAt, @Param("adminId") UUID adminId);

    void updatePost(@Param("id") UUID id, @Param("title") String title, @Param("slug") String slug,
                    @Param("excerpt") String excerpt, @Param("contentJson") String contentJson,
                    @Param("contentHtml") String contentHtml,
                    @Param("coverFileId") UUID coverFileId, @Param("status") String status,
                    @Param("publishedAt") OffsetDateTime publishedAt, @Param("adminId") UUID adminId);

    void softDeletePost(@Param("id") UUID id, @Param("adminId") UUID adminId);
}
