package com.fss.backend.file;

import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

@Repository
public class MongoFileAssetRepository implements FileAssetRepository {
    private final MongoTemplate mongo;

    public MongoFileAssetRepository(MongoTemplate mongo) {
        this.mongo = mongo;
    }

    @Override
    public void createUploading(FileDocument file) {
        mongo.insert(file);
    }

    @Override
    public boolean markUploaded(UUID id) {
        Instant now = Instant.now();
        return mongo.updateFirst(Query.query(Criteria.where("_id").is(id).and("status").is(FileStatus.UPLOADING)),
                new Update().set("status", FileStatus.INACTIVE).set("statusChangedAt", now).set("updatedAt", now),
                FileDocument.class).getMatchedCount() == 1;
    }

    @Override
    public FileAsset findById(UUID id) {
        if (id == null) return null;
        FileDocument doc = mongo.findById(id, FileDocument.class);
        return doc == null ? null : doc.toAsset();
    }

    @Override
    public FileAsset findByPath(String path) {
        if (path == null) return null;
        FileDocument doc = mongo.findOne(Query.query(Criteria.where("path").is(path)), FileDocument.class);
        return doc == null ? null : doc.toAsset();
    }

    @Override
    public Map<UUID, String> findPathsByIds(Collection<UUID> ids) {
        List<UUID> filtered = ids.stream().filter(Objects::nonNull).distinct().toList();
        if (filtered.isEmpty()) return Map.of();
        Query query = Query.query(Criteria.where("_id").in(filtered));
        query.fields().include("_id").include("path");
        return mongo.find(query, FileDocument.class).stream()
                .filter(doc -> doc.path() != null)
                .collect(Collectors.toMap(FileDocument::id, FileDocument::path));
    }

    @Override
    public ActivationResult activate(UUID id, UUID adminId) {
        Instant now = Instant.now();
        var result = mongo.updateFirst(
                Query.query(Criteria.where("_id").is(id).and("status").in(FileStatus.INACTIVE, FileStatus.ACTIVE)),
                new Update().set("status", FileStatus.ACTIVE).set("updatedBy", adminId).set("updatedAt", now)
                        .set("statusChangedAt", now),
                FileDocument.class);
        if (result.getMatchedCount() == 1) return ActivationResult.ACTIVATED;
        return mongo.exists(Query.query(Criteria.where("_id").is(id)), FileDocument.class)
                ? ActivationResult.UNAVAILABLE : ActivationResult.NOT_FOUND;
    }

    @Override
    public void deactivate(UUID id, UUID adminId) {
        Instant now = Instant.now();
        mongo.updateFirst(Query.query(Criteria.where("_id").is(id).and("status").is(FileStatus.ACTIVE)),
                new Update().set("status", FileStatus.INACTIVE).set("updatedBy", adminId).set("updatedAt", now)
                        .set("statusChangedAt", now),
                FileDocument.class);
    }

    @Override
    public FileDocument claimForDeletion(Instant now, Instant inactiveCutoff, Instant uploadingCutoff, Instant leaseUntil) {
        Criteria claimable = new Criteria().orOperator(
                Criteria.where("status").is(FileStatus.INACTIVE).and("statusChangedAt").lt(inactiveCutoff),
                Criteria.where("status").is(FileStatus.UPLOADING).and("createdAt").lt(uploadingCutoff),
                Criteria.where("status").is(FileStatus.DELETING).and("leaseUntil").lt(now));
        Query query = Query.query(claimable).with(Sort.by(Sort.Order.asc("statusChangedAt")));
        Update update = new Update().set("status", FileStatus.DELETING).set("leaseUntil", leaseUntil)
                .set("updatedAt", now);
        return mongo.findAndModify(query, update, FindAndModifyOptions.options().returnNew(false), FileDocument.class);
    }

    @Override
    public void restoreClaimed(UUID id, String status, Instant at) {
        Instant now = Instant.now();
        mongo.updateFirst(Query.query(Criteria.where("_id").is(id).and("status").is(FileStatus.DELETING)),
                new Update().set("status", status).set("statusChangedAt", at.isAfter(now) ? at : now).set("updatedAt", now)
                        .unset("leaseUntil").unset("lastError").set("deleteAttempts", 0),
                FileDocument.class);
    }

    @Override
    public void recordDeleteFailure(UUID id, String error, Instant retryAt) {
        mongo.updateFirst(Query.query(Criteria.where("_id").is(id).and("status").is(FileStatus.DELETING)),
                new Update().set("leaseUntil", retryAt).set("lastError", truncate(error)).inc("deleteAttempts", 1)
                        .set("updatedAt", Instant.now()),
                FileDocument.class);
    }

    @Override
    public void deleteClaimed(UUID id) {
        mongo.remove(Query.query(Criteria.where("_id").is(id).and("status").is(FileStatus.DELETING)), FileDocument.class);
    }

    @Override
    public void deleteUploading(UUID id) {
        mongo.remove(Query.query(Criteria.where("_id").is(id).and("status").is(FileStatus.UPLOADING)), FileDocument.class);
    }

    private static String truncate(String value) {
        if (value == null) return null;
        return value.length() > 500 ? value.substring(0, 500) : value;
    }
}
