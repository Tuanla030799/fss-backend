package com.fss.backend.content.banner;

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
import java.util.stream.Stream;

import static com.fss.backend.shared.mongo.MongoValues.hasText;
import static com.fss.backend.shared.mongo.MongoValues.instant;
import static com.fss.backend.shared.mongo.MongoValues.live;
import static com.fss.backend.shared.mongo.MongoValues.odt;

@Repository
public class BannerRepository {
    private final MongoTemplate mongo;
    private final FileAssetRepository files;

    public BannerRepository(MongoTemplate mongo, FileAssetRepository files) {
        this.mongo = mongo;
        this.files = files;
    }

    public List<LandingBanner> listBanners(boolean publicOnly, String status, int limit, int offset) {
        Query query = Query.query(filter(publicOnly, status))
                .with(Sort.by(Sort.Order.asc("sortOrder"), Sort.Order.desc("createdAt"), Sort.Order.desc("_id")))
                .skip(offset).limit(limit);
        return toBanners(mongo.find(query, BannerDocument.class));
    }

    public long countBanners(boolean publicOnly, String status) {
        return mongo.count(Query.query(filter(publicOnly, status)), BannerDocument.class);
    }

    public LandingBanner findBannerById(UUID id) {
        BannerDocument doc = mongo.findOne(Query.query(live().and("_id").is(id)), BannerDocument.class);
        return doc == null ? null : toBanners(List.of(doc)).get(0);
    }

    public void insertBanner(UUID id, String title, String subtitle, String linkUrl, UUID fileId, UUID mobileFileId,
                             String status, Integer sortOrder, OffsetDateTime startsAt, OffsetDateTime endsAt,
                             UUID adminId) {
        Instant now = Instant.now();
        mongo.insert(new BannerDocument(id, title, subtitle, linkUrl, fileId, mobileFileId, status, sortOrder,
                instant(startsAt), instant(endsAt), adminId, adminId, now, now, false, null));
    }

    public void updateBanner(UUID id, String title, String subtitle, String linkUrl, UUID fileId, UUID mobileFileId,
                             String status, Integer sortOrder, OffsetDateTime startsAt, OffsetDateTime endsAt,
                             UUID adminId) {
        mongo.updateFirst(Query.query(live().and("_id").is(id)), new Update().set("title", title).set("subtitle", subtitle)
                .set("linkUrl", linkUrl).set("fileId", fileId).set("mobileFileId", mobileFileId)
                .set("status", status).set("sortOrder", sortOrder)
                .set("startsAt", instant(startsAt)).set("endsAt", instant(endsAt)).set("updatedBy", adminId)
                .set("updatedAt", Instant.now()), BannerDocument.class);
    }

    public void softDeleteBanner(UUID id, UUID adminId) {
        Instant now = Instant.now();
        mongo.updateFirst(Query.query(live().and("_id").is(id)), new Update().set("deleted", true).set("deletedAt", now)
                .set("updatedBy", adminId).set("updatedAt", now), BannerDocument.class);
    }

    private Criteria filter(boolean publicOnly, String status) {
        Criteria criteria = live();
        if (publicOnly) {
            Instant now = Instant.now();
            criteria = criteria.and("status").is("ACTIVE").andOperator(
                    new Criteria().orOperator(Criteria.where("startsAt").is(null), Criteria.where("startsAt").lte(now)),
                    new Criteria().orOperator(Criteria.where("endsAt").is(null), Criteria.where("endsAt").gte(now)));
            if (hasText(status)) {
                criteria = new Criteria().andOperator(criteria, Criteria.where("status").is(status));
            }
        } else if (hasText(status)) {
            criteria = criteria.and("status").is(status);
        }
        return criteria;
    }

    private List<LandingBanner> toBanners(List<BannerDocument> docs) {
        Map<UUID, String> paths = files.findPathsByIds(docs.stream()
                .flatMap(doc -> Stream.of(doc.fileId(), doc.mobileFileId())).filter(Objects::nonNull).toList());
        return docs.stream().map(doc -> new LandingBanner(doc.id(), doc.title(), doc.subtitle(), doc.linkUrl(), doc.fileId(),
                doc.fileId() == null ? null : paths.get(doc.fileId()), doc.mobileFileId(),
                doc.mobileFileId() == null ? null : paths.get(doc.mobileFileId()), doc.status(), doc.sortOrder(),
                odt(doc.startsAt()), odt(doc.endsAt()), odt(doc.createdAt()))).toList();
    }
}
