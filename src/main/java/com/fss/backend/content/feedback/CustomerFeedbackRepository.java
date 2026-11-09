package com.fss.backend.content.feedback;

import com.fss.backend.file.FileAssetRepository;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import static com.fss.backend.shared.mongo.MongoValues.hasText;
import static com.fss.backend.shared.mongo.MongoValues.live;
import static com.fss.backend.shared.mongo.MongoValues.odt;

/** Returned {@code imageUrl} fields contain stored file paths; the service turns them into public URLs. */
@Repository
public class CustomerFeedbackRepository {
    private final MongoTemplate mongo;
    private final FileAssetRepository files;

    public CustomerFeedbackRepository(MongoTemplate mongo, FileAssetRepository files) {
        this.mongo = mongo;
        this.files = files;
    }

    public List<CustomerFeedback> list(boolean publicOnly, String status, int limit, int offset) {
        Query query = Query.query(filter(publicOnly, status))
                .with(Sort.by(Sort.Order.asc("sortOrder"), Sort.Order.desc("createdAt"), Sort.Order.desc("_id")))
                .skip(offset).limit(limit);
        return toFeedbacks(mongo.find(query, CustomerFeedbackDocument.class));
    }

    public long count(boolean publicOnly, String status) {
        return mongo.count(Query.query(filter(publicOnly, status)), CustomerFeedbackDocument.class);
    }

    public CustomerFeedback findById(UUID id) {
        CustomerFeedbackDocument doc = mongo.findOne(Query.query(live().and("_id").is(id)), CustomerFeedbackDocument.class);
        return doc == null ? null : toFeedbacks(List.of(doc)).get(0);
    }

    public void insert(UUID id, UUID fileId, String customerName, String description, int sortOrder, String status,
                       UUID adminId) {
        Instant now = Instant.now();
        mongo.insert(new CustomerFeedbackDocument(id, fileId, customerName, description, sortOrder, status, adminId,
                adminId, now, now, false, null));
    }

    public void update(UUID id, UUID fileId, String customerName, String description, int sortOrder, String status,
                       UUID adminId) {
        mongo.updateFirst(Query.query(live().and("_id").is(id)), new Update().set("fileId", fileId)
                .set("customerName", customerName).set("description", description).set("sortOrder", sortOrder)
                .set("status", status).set("updatedBy", adminId).set("updatedAt", Instant.now()),
                CustomerFeedbackDocument.class);
    }

    public void softDelete(UUID id, UUID adminId) {
        Instant now = Instant.now();
        mongo.updateFirst(Query.query(live().and("_id").is(id)), new Update().set("deleted", true).set("deletedAt", now)
                .set("updatedBy", adminId).set("updatedAt", now), CustomerFeedbackDocument.class);
    }

    private Criteria filter(boolean publicOnly, String status) {
        List<Criteria> and = new ArrayList<>(List.of(live()));
        if (publicOnly) and.add(Criteria.where("status").is("ACTIVE"));
        if (hasText(status)) and.add(Criteria.where("status").is(status));
        return new Criteria().andOperator(and);
    }

    private List<CustomerFeedback> toFeedbacks(List<CustomerFeedbackDocument> docs) {
        Map<UUID, String> paths = files.findPathsByIds(docs.stream().map(CustomerFeedbackDocument::fileId)
                .filter(Objects::nonNull).toList());
        return docs.stream().map(doc -> new CustomerFeedback(doc.id(), doc.fileId(),
                doc.fileId() == null ? null : paths.get(doc.fileId()), doc.customerName(), doc.description(),
                doc.sortOrder(), doc.status(), odt(doc.createdAt()), odt(doc.updatedAt()))).toList();
    }
}
