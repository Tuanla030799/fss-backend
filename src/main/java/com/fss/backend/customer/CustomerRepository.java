package com.fss.backend.customer;

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
import java.util.regex.Pattern;

import static com.fss.backend.shared.mongo.MongoValues.containsIgnoreCase;
import static com.fss.backend.shared.mongo.MongoValues.hasText;
import static com.fss.backend.shared.mongo.MongoValues.live;
import static com.fss.backend.shared.mongo.MongoValues.odt;

@Repository
public class CustomerRepository {
    private final MongoTemplate mongo;

    public CustomerRepository(MongoTemplate mongo) {
        this.mongo = mongo;
    }

    public List<Customer> listCustomers(String status, String keyword, int limit, int offset) {
        Query query = Query.query(filter(status, keyword)).with(Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("_id")))
                .skip(offset).limit(limit);
        return mongo.find(query, CustomerDocument.class).stream().map(this::toCustomer).toList();
    }

    public long countCustomers(String status, String keyword) {
        return mongo.count(Query.query(filter(status, keyword)), CustomerDocument.class);
    }

    public Customer findCustomerById(UUID id) {
        return toCustomer(mongo.findOne(Query.query(live().and("_id").is(id)), CustomerDocument.class));
    }

    public boolean emailExists(String email, UUID excludeId) {
        Criteria criteria = live().and("emailKey").is(MongoValues.lower(email));
        if (excludeId != null) criteria = criteria.and("_id").ne(excludeId);
        return mongo.exists(Query.query(criteria), CustomerDocument.class);
    }

    public boolean phoneExists(String phone, UUID excludeId) {
        Criteria criteria = live().and("phone").is(phone);
        if (excludeId != null) criteria = criteria.and("_id").ne(excludeId);
        return mongo.exists(Query.query(criteria), CustomerDocument.class);
    }

    public void insertCustomer(UUID id, String fullName, String email, String phone, String status) {
        Instant now = Instant.now();
        mongo.insert(new CustomerDocument(id, fullName, email, MongoValues.lower(email), phone, null, status, now, now, false, null));
    }

    public void updateCustomer(UUID id, String fullName, String email, String phone, String status) {
        Update update = new Update().set("fullName", fullName).set("email", email).set("phone", phone).set("status", status)
                .set("updatedAt", Instant.now());
        if (email == null) update.unset("emailKey"); else update.set("emailKey", MongoValues.lower(email));
        mongo.updateFirst(Query.query(live().and("_id").is(id)), update, CustomerDocument.class);
    }

    public void softDeleteCustomer(UUID id) {
        Instant now = Instant.now();
        mongo.updateFirst(Query.query(live().and("_id").is(id)), new Update().set("deleted", true).set("deletedAt", now)
                .set("status", "INACTIVE").set("updatedAt", now), CustomerDocument.class);
    }

    private Criteria filter(String status, String keyword) {
        Criteria criteria = live();
        if (hasText(status)) criteria = criteria.and("status").is(status);
        if (hasText(keyword)) {
            Pattern pattern = containsIgnoreCase(keyword);
            criteria = criteria.orOperator(Criteria.where("fullName").regex(pattern), Criteria.where("email").regex(pattern),
                    Criteria.where("phone").regex(pattern));
        }
        return criteria;
    }

    private Customer toCustomer(CustomerDocument doc) {
        return doc == null ? null : new Customer(doc.id(), doc.fullName(), doc.email(), doc.phone(), doc.status(), odt(doc.createdAt()));
    }
}
