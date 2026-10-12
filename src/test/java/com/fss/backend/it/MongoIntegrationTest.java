package com.fss.backend.it;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fss.backend.auth.JwtProvider;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.containers.MongoDBContainer;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;

/**
 * Boots the full application against a real MongoDB 7 single-node replica set (Testcontainers), so transactions,
 * unique indexes and conditional updates are exercised for real, not mocked.
 */
@SpringBootTest(properties = {
        "app.storage.provider=local",
        "app.upload-dir=build/tmp/it-uploads",
        "app.file-cleanup.initial-delay-ms=3600000",
        "app.auth.allow-public-admin-registration=false"
})
@AutoConfigureMockMvc
public abstract class MongoIntegrationTest {
    static final MongoDBContainer MONGO = new MongoDBContainer("mongo:7.0");

    static {
        MONGO.start();
    }

    @DynamicPropertySource
    static void mongoProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.mongodb.uri", () -> MONGO.getReplicaSetUrl("fss_it"));
        registry.add("spring.data.mongodb.database", () -> "fss_it");
    }

    @Autowired
    protected MockMvc mvc;
    @Autowired
    protected ObjectMapper json;
    @Autowired
    protected MongoTemplate mongo;
    @Autowired
    protected JwtProvider jwtProvider;

    protected String adminToken;

    @BeforeEach
    void adminToken() {
        adminToken = "Bearer " + jwtProvider.newAccessToken(UUID.randomUUID(), "super_admin");
    }

    protected MockHttpServletRequestBuilder admin(MockHttpServletRequestBuilder request) {
        return request.header("Authorization", adminToken);
    }

    protected MockHttpServletRequestBuilder jsonBody(MockHttpServletRequestBuilder request, Object body) throws Exception {
        return request.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(body));
    }

    protected JsonNode body(MvcResult result) throws Exception {
        // BigDecimal nodes keep the scale sent by the API (e.g. 10.00), like a JS client reading the raw JSON text.
        return json.copy()
                .setNodeFactory(com.fasterxml.jackson.databind.node.JsonNodeFactory.withExactBigDecimals(true))
                .enable(com.fasterxml.jackson.databind.DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
                .readTree(result.getResponse().getContentAsByteArray());
    }

    protected JsonNode uploadImage(byte[] bytes, String name) throws Exception {
        MvcResult result = mvc.perform(admin(multipart("/api/admin/files/upload")
                        .file(new MockMultipartFile("file", name, "image/png", bytes))))
                .andReturn();
        return body(result).get("data");
    }

    protected static String unique(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().substring(0, 8);
    }
}
