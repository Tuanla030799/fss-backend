package com.fss.backend.content.blog;

import com.fss.backend.file.FileAssetRepository;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

import static com.fss.backend.shared.mongo.MongoValues.containsIgnoreCase;
import static com.fss.backend.shared.mongo.MongoValues.hasText;
import static com.fss.backend.shared.mongo.MongoValues.instant;
import static com.fss.backend.shared.mongo.MongoValues.live;
import static com.fss.backend.shared.mongo.MongoValues.odt;

@Repository
public class BlogPostRepository {
    private final MongoTemplate mongo;
    private final FileAssetRepository files;

    public BlogPostRepository(MongoTemplate mongo, FileAssetRepository files) {
        this.mongo = mongo;
        this.files = files;
    }

    public List<BlogPost> listPosts(boolean publicOnly, String status, String keyword, int limit, int offset) {
        // Old: ORDER BY published_at DESC NULLS LAST, created_at DESC. Descending sort puts nulls last in MongoDB too.
        Query query = Query.query(filter(publicOnly, status, keyword))
                .with(Sort.by(Sort.Order.desc("publishedAt"), Sort.Order.desc("createdAt"), Sort.Order.desc("_id")))
                .skip(offset).limit(limit);
        return toPosts(mongo.find(query, BlogPostDocument.class));
    }

    public long countPosts(boolean publicOnly, String status, String keyword) {
        return mongo.count(Query.query(filter(publicOnly, status, keyword)), BlogPostDocument.class);
    }

    public BlogPost findPostById(UUID id) {
        BlogPostDocument doc = mongo.findOne(Query.query(live().and("_id").is(id)), BlogPostDocument.class);
        return doc == null ? null : toPosts(List.of(doc)).get(0);
    }

    public BlogPost findPostBySlug(String slug, boolean publicOnly) {
        Criteria criteria = live().and("slug").is(slug);
        if (publicOnly) criteria = new Criteria().andOperator(criteria, publicCriteria());
        BlogPostDocument doc = mongo.findOne(Query.query(criteria), BlogPostDocument.class);
        return doc == null ? null : toPosts(List.of(doc)).get(0);
    }

    public boolean slugExists(String slug, UUID excludeId) {
        Criteria criteria = live().and("slug").is(slug);
        if (excludeId != null) criteria = criteria.and("_id").ne(excludeId);
        return mongo.exists(Query.query(criteria), BlogPostDocument.class);
    }

    public void insertPost(UUID id, String title, String slug, String excerpt, String contentHtml, List<String> htmlFilePaths,
                           UUID coverFileId, String status, OffsetDateTime publishedAt, UUID adminId) {
        Instant now = Instant.now();
        mongo.insert(new BlogPostDocument(id, title, slug, excerpt, contentHtml, htmlFilePaths, coverFileId, status,
                instant(publishedAt), adminId, adminId, now, now, false, null));
    }

    public void updatePost(UUID id, String title, String slug, String excerpt, String contentHtml, List<String> htmlFilePaths,
                           UUID coverFileId, String status, OffsetDateTime publishedAt, UUID adminId) {
        mongo.updateFirst(Query.query(live().and("_id").is(id)), new Update().set("title", title).set("slug", slug)
                .set("excerpt", excerpt).set("contentHtml", contentHtml).set("htmlFilePaths", htmlFilePaths)
                .set("coverFileId", coverFileId).set("status", status).set("publishedAt", instant(publishedAt))
                .set("updatedBy", adminId).set("updatedAt", Instant.now()), BlogPostDocument.class);
    }

    public void softDeletePost(UUID id, UUID adminId) {
        Instant now = Instant.now();
        mongo.updateFirst(Query.query(live().and("_id").is(id)), new Update().set("deleted", true).set("deletedAt", now)
                .set("updatedBy", adminId).set("updatedAt", now), BlogPostDocument.class);
    }

    private Criteria filter(boolean publicOnly, String status, String keyword) {
        java.util.ArrayList<Criteria> and = new java.util.ArrayList<>(List.of(live()));
        if (publicOnly) and.add(publicCriteria());
        if (hasText(status)) and.add(Criteria.where("status").is(status));
        if (hasText(keyword)) {
            Pattern pattern = containsIgnoreCase(keyword);
            and.add(new Criteria().orOperator(Criteria.where("title").regex(pattern), Criteria.where("excerpt").regex(pattern)));
        }
        return new Criteria().andOperator(and);
    }

    private static Criteria publicCriteria() {
        return new Criteria().andOperator(Criteria.where("status").is("ACTIVE"),
                new Criteria().orOperator(Criteria.where("publishedAt").is(null), Criteria.where("publishedAt").lte(Instant.now())));
    }

    private List<BlogPost> toPosts(List<BlogPostDocument> docs) {
        Map<UUID, String> paths = files.findPathsByIds(docs.stream().map(BlogPostDocument::coverFileId).filter(Objects::nonNull).toList());
        return docs.stream().map(doc -> new BlogPost(doc.id(), doc.title(), doc.slug(), doc.excerpt(), "{}", doc.contentHtml(),
                doc.coverFileId(), doc.coverFileId() == null ? null : paths.get(doc.coverFileId()), doc.status(),
                odt(doc.publishedAt()), odt(doc.createdAt()), odt(doc.updatedAt()))).toList();
    }
}
