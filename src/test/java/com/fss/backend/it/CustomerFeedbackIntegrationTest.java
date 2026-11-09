package com.fss.backend.it;

import com.fasterxml.jackson.databind.JsonNode;
import com.fss.backend.file.FileDocument;
import com.fss.backend.support.TestImages;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Customer feedback photos on the home page: image required, name and description optional. */
class CustomerFeedbackIntegrationTest extends MongoIntegrationTest {
    @Test
    void crudFollowsFileLifecycleAndPublicListIsActiveOnlyInOrder() throws Exception {
        JsonNode first = uploadImage(TestImages.png(30, 30, false), "first.png");
        JsonNode second = uploadImage(TestImages.png(30, 30, false), "second.png");
        JsonNode replacement = uploadImage(TestImages.png(30, 30, false), "replacement.png");

        String firstId = create(Map.of("fileId", first.get("fileId").asText(), "sortOrder", 2));
        Map<String, Object> secondBody = new HashMap<>(Map.of("fileId", second.get("fileId").asText(), "sortOrder", 1,
                "customerName", "  Chị Lan ", "description", "Giày êm, giao nhanh"));
        String secondId = create(secondBody);
        String hiddenId = create(Map.of("fileId", replacement.get("fileId").asText(), "status", "INACTIVE"));
        assertEquals("ACTIVE", fileStatus(first));

        List<String> publicIds = publicIds();
        assertTrue(publicIds.indexOf(secondId) < publicIds.indexOf(firstId), "sortOrder ascending");
        assertFalse(publicIds.contains(hiddenId));

        JsonNode detail = body(mvc.perform(admin(get("/api/admin/customer-feedbacks/" + secondId)))
                .andExpect(status().isOk()).andReturn()).get("data");
        assertEquals("Chị Lan", detail.get("customerName").asText());
        assertEquals(second.get("url").asText(), detail.get("imageUrl").asText());

        JsonNode firstDetail = body(mvc.perform(admin(get("/api/admin/customer-feedbacks/" + firstId)))
                .andExpect(status().isOk()).andReturn()).get("data");
        assertTrue(firstDetail.get("customerName").isNull());
        assertTrue(firstDetail.get("description").isNull());

        // Replacing the image releases the old file; deleting releases the current one.
        mvc.perform(admin(delete("/api/admin/customer-feedbacks/" + hiddenId))).andExpect(status().isOk());
        secondBody.put("fileId", replacement.get("fileId").asText());
        mvc.perform(admin(jsonBody(put("/api/admin/customer-feedbacks/" + secondId), secondBody))).andExpect(status().isOk());
        assertEquals("INACTIVE", fileStatus(second));
        assertEquals("ACTIVE", fileStatus(replacement));
        mvc.perform(admin(delete("/api/admin/customer-feedbacks/" + secondId))).andExpect(status().isOk());
        assertEquals("INACTIVE", fileStatus(replacement));

        mvc.perform(admin(get("/api/admin/customer-feedbacks/" + secondId))).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CUSTOMER_FEEDBACK_NOT_FOUND"));
        mvc.perform(admin(jsonBody(post("/api/admin/customer-feedbacks"), Map.of("customerName", "x"))))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/admin/customer-feedbacks")).andExpect(status().isUnauthorized());
    }

    private String create(Map<String, Object> body) throws Exception {
        return body(mvc.perform(admin(jsonBody(post("/api/admin/customer-feedbacks"), body)))
                .andExpect(status().isOk()).andReturn()).get("data").get("id").asText();
    }

    private List<String> publicIds() throws Exception {
        JsonNode items = body(mvc.perform(get("/api/customer-feedbacks?limit=100")).andExpect(status().isOk()).andReturn())
                .get("data").get("items");
        List<String> ids = new ArrayList<>();
        items.forEach(item -> ids.add(item.get("id").asText()));
        return ids;
    }

    private String fileStatus(JsonNode upload) {
        return mongo.findById(UUID.fromString(upload.get("fileId").asText()), FileDocument.class).status();
    }
}
