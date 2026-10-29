package com.fss.backend.content.page;

import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static com.fss.backend.shared.mongo.MongoValues.odt;

@Repository
public class SitePageRepository {
    private final MongoTemplate mongo;

    public SitePageRepository(MongoTemplate mongo) {
        this.mongo = mongo;
    }

    /** The saved page, or {@code null} when it has never been saved. */
    public SitePage findPage(SitePageKey key) {
        SitePageDocument doc = mongo.findById(key.key(), SitePageDocument.class);
        return doc == null ? null : toPage(doc);
    }

    public void upsertPage(SitePageKey key, String title, String summary, String contentHtml, List<String> htmlFilePaths,
                           UUID adminId) {
        Instant now = Instant.now();
        mongo.upsert(Query.query(Criteria.where("_id").is(key.key())), new Update().set("title", title)
                .set("summary", summary).set("contentHtml", contentHtml).set("htmlFilePaths", htmlFilePaths)
                .set("updatedBy", adminId).set("updatedAt", now).setOnInsert("createdBy", adminId)
                .setOnInsert("createdAt", now).setOnInsert("deleted", false), SitePageDocument.class);
    }

    private static SitePage toPage(SitePageDocument doc) {
        return new SitePage(doc.id(), doc.title(), doc.summary(), doc.contentHtml(), odt(doc.createdAt()), odt(doc.updatedAt()));
    }
}
