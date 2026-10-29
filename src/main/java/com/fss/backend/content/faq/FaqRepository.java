package com.fss.backend.content.faq;

import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static com.fss.backend.shared.mongo.MongoValues.containsIgnoreCase;
import static com.fss.backend.shared.mongo.MongoValues.hasText;
import static com.fss.backend.shared.mongo.MongoValues.live;
import static com.fss.backend.shared.mongo.MongoValues.odt;

@Repository
public class FaqRepository {
    private final MongoTemplate mongo;

    public FaqRepository(MongoTemplate mongo) {
        this.mongo = mongo;
    }

    public List<Faq> listFaqs(boolean publicOnly, String status, String keyword, int limit, int offset) {
        Query query = Query.query(filter(publicOnly, status, keyword))
                .with(Sort.by(Sort.Order.asc("sortOrder"), Sort.Order.asc("createdAt"), Sort.Order.asc("_id")))
                .skip(offset).limit(limit);
        return mongo.find(query, FaqDocument.class).stream().map(FaqRepository::toFaq).toList();
    }

    public long countFaqs(boolean publicOnly, String status, String keyword) {
        return mongo.count(Query.query(filter(publicOnly, status, keyword)), FaqDocument.class);
    }

    public Faq findFaqById(UUID id) {
        FaqDocument doc = mongo.findOne(Query.query(live().and("_id").is(id)), FaqDocument.class);
        return doc == null ? null : toFaq(doc);
    }

    public void insertFaq(UUID id, String question, String answerHtml, List<String> htmlFilePaths, int sortOrder,
                          String status, UUID adminId) {
        Instant now = Instant.now();
        mongo.insert(new FaqDocument(id, question, answerHtml, htmlFilePaths, sortOrder, status, adminId, adminId, now, now,
                false, null));
    }

    public void updateFaq(UUID id, String question, String answerHtml, List<String> htmlFilePaths, int sortOrder,
                          String status, UUID adminId) {
        mongo.updateFirst(Query.query(live().and("_id").is(id)), new Update().set("question", question)
                .set("answerHtml", answerHtml).set("htmlFilePaths", htmlFilePaths).set("sortOrder", sortOrder)
                .set("status", status).set("updatedBy", adminId).set("updatedAt", Instant.now()), FaqDocument.class);
    }

    public void softDeleteFaq(UUID id, UUID adminId) {
        Instant now = Instant.now();
        mongo.updateFirst(Query.query(live().and("_id").is(id)), new Update().set("deleted", true).set("deletedAt", now)
                .set("updatedBy", adminId).set("updatedAt", now), FaqDocument.class);
    }

    private Criteria filter(boolean publicOnly, String status, String keyword) {
        List<Criteria> and = new ArrayList<>(List.of(live()));
        if (publicOnly) and.add(Criteria.where("status").is("ACTIVE"));
        if (hasText(status)) and.add(Criteria.where("status").is(status));
        if (hasText(keyword)) and.add(Criteria.where("question").regex(containsIgnoreCase(keyword.trim())));
        return new Criteria().andOperator(and);
    }

    private static Faq toFaq(FaqDocument doc) {
        return new Faq(doc.id(), doc.question(), doc.answerHtml(), doc.sortOrder(), doc.status(), odt(doc.createdAt()),
                odt(doc.updatedAt()));
    }
}
