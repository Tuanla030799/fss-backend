package com.fss.backend.auth;

import com.fss.backend.shared.mongo.MongoValues;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.UUID;

import static com.fss.backend.shared.mongo.MongoValues.live;

@Repository
public class AuthRepository {
    private final MongoTemplate mongo;

    public AuthRepository(MongoTemplate mongo) {
        this.mongo = mongo;
    }

    public AdminUser findAdminByEmail(String email) {
        if (email == null) return null;
        return toAdmin(mongo.findOne(Query.query(live().and("emailKey").is(MongoValues.lower(email)).and("status").is("ACTIVE")),
                AdminUserDocument.class));
    }

    public AdminUser findAdminById(UUID id) {
        return toAdmin(mongo.findOne(Query.query(live().and("_id").is(id).and("status").is("ACTIVE")), AdminUserDocument.class));
    }

    public boolean adminEmailExists(String email) {
        return mongo.exists(Query.query(live().and("emailKey").is(MongoValues.lower(email))), AdminUserDocument.class);
    }

    public void insertAdmin(UUID id, String name, String email, String passwordHash, String role) {
        Instant now = Instant.now();
        mongo.insert(new AdminUserDocument(id, name, email, MongoValues.lower(email), passwordHash,
                role == null ? "operator" : role, "ACTIVE", now, now, false, null));
    }

    public void insertRefreshToken(UUID id, UUID adminId, String token, OffsetDateTime expiresAt) {
        mongo.insert(new RefreshTokenDocument(id, adminId, token, expiresAt.toInstant(), null, Instant.now()));
    }

    public UUID findAdminIdByRefreshToken(String token) {
        RefreshTokenDocument found = mongo.findOne(Query.query(Criteria.where("token").is(token)
                .and("revokedAt").is(null).and("expiresAt").gt(Instant.now())), RefreshTokenDocument.class);
        return found == null ? null : found.adminId();
    }

    /** Revokes the token; returns true only for the caller that revoked it (prevents refresh-token reuse races). */
    public boolean revokeRefreshToken(String token) {
        return mongo.updateFirst(Query.query(Criteria.where("token").is(token).and("revokedAt").is(null)),
                Update.update("revokedAt", Instant.now()), RefreshTokenDocument.class).getModifiedCount() == 1;
    }

    private AdminUser toAdmin(AdminUserDocument doc) {
        return doc == null ? null : new AdminUser(doc.id(), doc.name(), doc.email(), doc.passwordHash(), doc.role(),
                MongoValues.odt(doc.createdAt()));
    }
}
