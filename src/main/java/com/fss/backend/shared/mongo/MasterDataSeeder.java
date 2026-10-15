package com.fss.backend.shared.mongo;

import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Small idempotent seed for an empty database (what the old Flyway V2/V8/V17 scripts inserted):
 * <ul>
 *   <li>colors and shoe sizes 35.5..52.5 plus the {@code DEFAULT} size used by products without sizes, only when the
 *       collection is empty;</li>
 *   <li>a first admin from {@code APP_BOOTSTRAP_ADMIN_EMAIL}/{@code APP_BOOTSTRAP_ADMIN_PASSWORD}, only when there
 *       is no admin at all (no password hash is committed to the repository).</li>
 * </ul>
 * Data migrated from PostgreSQL already contains these rows, so nothing is inserted then.
 */
@Component
@Order(1)
public class MasterDataSeeder implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(MasterDataSeeder.class);
    private static final List<String[]> COLORS = List.of(
            new String[]{"black", "#000000"}, new String[]{"blue", "#0000FF"}, new String[]{"brown", "#A52A2A"},
            new String[]{"green", "#008000"}, new String[]{"gray", "#808080"}, new String[]{"multi-color", null},
            new String[]{"orange", "#FFA500"}, new String[]{"pink", "#FFC0CB"}, new String[]{"purple", "#800080"},
            new String[]{"red", "#FF0000"}, new String[]{"white", "#FFFFFF"}, new String[]{"yellow", "#FFFF00"});

    private final MongoTemplate mongo;
    private final PasswordEncoder passwordEncoder;
    private final boolean seedMasterData;
    private final String adminEmail;
    private final String adminPassword;

    public MasterDataSeeder(MongoTemplate mongo, PasswordEncoder passwordEncoder,
                            @Value("${app.seed.master-data:true}") boolean seedMasterData,
                            @Value("${app.bootstrap-admin.email:}") String adminEmail,
                            @Value("${app.bootstrap-admin.password:}") String adminPassword) {
        this.mongo = mongo;
        this.passwordEncoder = passwordEncoder;
        this.seedMasterData = seedMasterData;
        this.adminEmail = adminEmail;
        this.adminPassword = adminPassword;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (seedMasterData) {
            seedColors();
            seedSizes();
        }
        seedAdmin();
    }

    private void seedColors() {
        if (mongo.estimatedCount("colors") > 0) return;
        Instant now = Instant.now();
        int order = 10;
        for (String[] color : COLORS) {
            mongo.insert(base(now).append("name", color[0]).append("nameKey", color[0]).append("colorCode", color[1])
                    .append("status", "ACTIVE").append("sortOrder", order), "colors");
            order += 10;
        }
        log.info("Seeded {} colors", COLORS.size());
    }

    private void seedSizes() {
        if (mongo.estimatedCount("sizes") > 0) return;
        Instant now = Instant.now();
        mongo.insert(base(now).append("value", "DEFAULT").append("valueKey", "default").append("label", "Default")
                .append("status", "ACTIVE").append("sortOrder", 0), "sizes");
        for (int half = 71; half <= 105; half++) {
            String value = new BigDecimal(half).divide(BigDecimal.valueOf(2)).stripTrailingZeros().toPlainString();
            mongo.insert(base(now).append("value", value).append("valueKey", value).append("label", value)
                    .append("status", "ACTIVE").append("sortOrder", half - 70), "sizes");
        }
        log.info("Seeded default shoe sizes");
    }

    private void seedAdmin() {
        if (adminEmail == null || adminEmail.isBlank() || adminPassword == null || adminPassword.isBlank()) return;
        if (mongo.exists(new Query(), "admin_users")) return;
        if (adminPassword.length() < 12) {
            log.warn("APP_BOOTSTRAP_ADMIN_PASSWORD must have at least 12 characters; bootstrap admin not created");
            return;
        }
        String email = adminEmail.trim();
        mongo.insert(base(Instant.now()).append("name", "Super Admin").append("email", email)
                .append("emailKey", email.toLowerCase(Locale.ROOT)).append("passwordHash", passwordEncoder.encode(adminPassword))
                .append("role", "super_admin").append("status", "ACTIVE"), "admin_users");
        log.info("Bootstrap admin created email={}", email);
    }

    private static Document base(Instant now) {
        return new Document("_id", UUID.randomUUID()).append("createdAt", now).append("updatedAt", now).append("deleted", false);
    }
}
