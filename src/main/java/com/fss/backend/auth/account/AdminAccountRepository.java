package com.fss.backend.auth.account;

import com.fss.backend.auth.AdminUserDocument;
import com.fss.backend.shared.mongo.MongoValues;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static com.fss.backend.shared.mongo.MongoValues.containsIgnoreCase;
import static com.fss.backend.shared.mongo.MongoValues.hasText;
import static com.fss.backend.shared.mongo.MongoValues.live;

@Repository
public class AdminAccountRepository {
    private final MongoTemplate mongo;

    public AdminAccountRepository(MongoTemplate mongo) {
        this.mongo = mongo;
    }

    public List<AdminAccount> listAdminAccounts(String keyword, int limit, int offset) {
        Query query = Query.query(filter(keyword)).with(Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("_id")))
                .skip(offset).limit(limit);
        return mongo.find(query, AdminUserDocument.class).stream().map(this::toAccount).toList();
    }

    public long countAdminAccounts(String keyword) {
        return mongo.count(Query.query(filter(keyword)), AdminUserDocument.class);
    }

    public AdminAccount findAdminAccountById(UUID id) {
        return toAccount(mongo.findOne(Query.query(live().and("_id").is(id)), AdminUserDocument.class));
    }

    public boolean adminEmailExists(String email, UUID excludeId) {
        Criteria criteria = live().and("emailKey").is(MongoValues.lower(email));
        if (excludeId != null) criteria = criteria.and("_id").ne(excludeId);
        return mongo.exists(Query.query(criteria), AdminUserDocument.class);
    }

    public void insertAdminAccount(UUID id, String name, String email, String passwordHash, String role, String status) {
        Instant now = Instant.now();
        mongo.insert(new AdminUserDocument(id, name, email, MongoValues.lower(email), passwordHash, role, status,
                now, now, false, null));
    }

    public void updateAdminAccount(UUID id, String name, String email, String passwordHash, String role, String status) {
        Update update = new Update().set("name", name).set("email", email).set("emailKey", MongoValues.lower(email))
                .set("role", role).set("status", status).set("updatedAt", Instant.now());
        if (passwordHash != null) update.set("passwordHash", passwordHash);
        mongo.updateFirst(Query.query(live().and("_id").is(id)), update, AdminUserDocument.class);
    }

    public void softDeleteAdminAccount(UUID id) {
        Instant now = Instant.now();
        mongo.updateFirst(Query.query(live().and("_id").is(id)), new Update().set("deleted", true).set("deletedAt", now)
                .set("status", "INACTIVE").set("updatedAt", now), AdminUserDocument.class);
    }

    private Criteria filter(String keyword) {
        Criteria criteria = live();
        if (hasText(keyword)) {
            criteria = criteria.orOperator(Criteria.where("name").regex(containsIgnoreCase(keyword)),
                    Criteria.where("email").regex(containsIgnoreCase(keyword)));
        }
        return criteria;
    }

    private AdminAccount toAccount(AdminUserDocument doc) {
        return doc == null ? null : new AdminAccount(doc.id(), doc.name(), doc.email(), doc.role(), doc.status(),
                MongoValues.odt(doc.createdAt()));
    }
}
