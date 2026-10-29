package com.fss.backend.it;

import com.fasterxml.jackson.databind.JsonNode;
import com.fss.backend.file.FileDocument;
import com.fss.backend.support.TestImages;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Landing banners carry a desktop image and an optional mobile image; both follow the file lifecycle. */
class BannerImagesIntegrationTest extends MongoIntegrationTest {
    @Test
    void mobileImageIsOptionalReturnedAndReleasedLikeTheDesktopImage() throws Exception {
        JsonNode desktop = uploadImage(TestImages.png(40, 16, false), "desktop.png");
        JsonNode mobile = uploadImage(TestImages.png(20, 25, false), "mobile.png");
        assertEquals("INACTIVE", fileStatus(mobile));

        String title = unique("Banner");
        Map<String, Object> body = new HashMap<>(Map.of("title", title, "fileId", desktop.get("fileId").asText(),
                "mobileFileId", mobile.get("fileId").asText()));
        String id = body(mvc.perform(admin(jsonBody(post("/api/admin/landing-banners"), body)))
                .andExpect(status().isOk()).andReturn()).get("data").get("id").asText();
        assertEquals("ACTIVE", fileStatus(desktop));
        assertEquals("ACTIVE", fileStatus(mobile));

        JsonNode banner = publicBanner(id);
        assertEquals(desktop.get("url").asText(), banner.get("imageUrl").asText());
        assertEquals(mobile.get("fileId").asText(), banner.get("mobileFileId").asText());
        assertEquals(mobile.get("url").asText(), banner.get("mobileImageUrl").asText());

        // Removing the mobile image keeps the banner on the desktop image and releases the mobile file.
        body.remove("mobileFileId");
        mvc.perform(admin(jsonBody(put("/api/admin/landing-banners/" + id), body))).andExpect(status().isOk());
        banner = publicBanner(id);
        assertTrue(banner.get("mobileFileId").isNull(), banner.toString());
        assertTrue(banner.get("mobileImageUrl").isNull(), banner.toString());
        assertEquals("INACTIVE", fileStatus(mobile));
        assertEquals("ACTIVE", fileStatus(desktop));

        // Deleting the banner releases both images.
        body.put("mobileFileId", mobile.get("fileId").asText());
        mvc.perform(admin(jsonBody(put("/api/admin/landing-banners/" + id), body))).andExpect(status().isOk());
        assertEquals("ACTIVE", fileStatus(mobile));
        mvc.perform(admin(delete("/api/admin/landing-banners/" + id))).andExpect(status().isOk());
        assertEquals("INACTIVE", fileStatus(desktop));
        assertEquals("INACTIVE", fileStatus(mobile));
    }

    @Test
    void bannerWithoutImagesStillLists() throws Exception {
        String id = body(mvc.perform(admin(jsonBody(post("/api/admin/landing-banners"), Map.of("title", unique("Text")))))
                .andExpect(status().isOk()).andReturn()).get("data").get("id").asText();
        JsonNode banner = publicBanner(id);
        assertTrue(banner.get("imageUrl").isNull(), banner.toString());
        assertTrue(banner.get("mobileImageUrl").isNull(), banner.toString());
    }

    private JsonNode publicBanner(String id) throws Exception {
        JsonNode items = body(mvc.perform(get("/api/landing-banners?limit=100")).andExpect(status().isOk()).andReturn())
                .get("data").get("items");
        for (JsonNode item : items) {
            if (item.get("id").asText().equals(id)) return item;
        }
        throw new AssertionError("Banner " + id + " not in public list");
    }

    private String fileStatus(JsonNode upload) {
        return mongo.findById(UUID.fromString(upload.get("fileId").asText()), FileDocument.class).status();
    }
}
