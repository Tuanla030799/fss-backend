package com.fss.backend.it;

import com.fasterxml.jackson.databind.JsonNode;
import com.fss.backend.content.page.SitePageDocument;
import com.fss.backend.file.FileDocument;
import com.fss.backend.support.TestImages;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Admin-editable site pages (about, purchase & shipping) and FAQs. */
class SiteContentIntegrationTest extends MongoIntegrationTest {
    @Test
    void unsavedPageReturnsDefaultsAndUnknownKeysAre404() throws Exception {
        mongo.remove(Query.query(Criteria.where("_id").is("purchase-shipping")), SitePageDocument.class);

        JsonNode page = publicData("/api/pages/purchase-shipping");
        assertEquals("purchase-shipping", page.get("key").asText());
        assertEquals("Cách mua hàng và hình thức vận chuyển", page.get("title").asText());
        assertTrue(page.get("summary").isNull(), page.toString());
        assertTrue(page.get("contentHtml").isNull(), page.toString());
        assertTrue(page.get("updatedAt").isNull(), page.toString());

        error(get("/api/pages/missing"), 404, "SITE_PAGE_NOT_FOUND");
        error(admin(get("/api/admin/pages/missing")), 404, "SITE_PAGE_NOT_FOUND");
        error(admin(jsonBody(put("/api/admin/pages/missing"), Map.of("title", "X"))), 404, "SITE_PAGE_NOT_FOUND");
    }

    @Test
    void adminSavesSanitizedPageAndListsEveryKeyInOrder() throws Exception {
        mvc.perform(admin(jsonBody(put("/api/admin/pages/about"), Map.of("title", "  Về The Pocket Shoes  ",
                        "summary", "Câu chuyện của chúng tôi", "contentHtml", "<h2>Câu chuyện</h2><p>Xin chào</p><script>alert(1)</script>"))))
                .andExpect(status().isOk());

        JsonNode page = publicData("/api/pages/about");
        assertEquals("about", page.get("key").asText());
        assertEquals("Về The Pocket Shoes", page.get("title").asText());
        assertEquals("Câu chuyện của chúng tôi", page.get("summary").asText());
        assertEquals("<h2>Câu chuyện</h2><p>Xin chào</p>", page.get("contentHtml").asText());
        assertFalse(page.get("updatedAt").isNull(), page.toString());

        // A blank summary is stored as null; an update keeps createdAt.
        String createdAt = page.get("createdAt").asText();
        mvc.perform(admin(jsonBody(put("/api/admin/pages/about"), Map.of("title", "About", "summary", "  ",
                "contentHtml", "<p>Mới</p>")))).andExpect(status().isOk());
        JsonNode updated = adminData("/api/admin/pages/about");
        assertTrue(updated.get("summary").isNull(), updated.toString());
        assertEquals(createdAt, updated.get("createdAt").asText());

        JsonNode list = adminData("/api/admin/pages");
        List<String> keys = new ArrayList<>();
        list.get("items").forEach(item -> keys.add(item.get("key").asText()));
        assertEquals(List.of("about", "purchase-shipping"), keys);
        assertEquals(2, list.get("total").asInt());
        assertEquals(1, list.get("totalPages").asInt());

        error(admin(jsonBody(put("/api/admin/pages/about"), Map.of("title", " "))), 400, "VALIDATION_ERROR");
    }

    @Test
    void adminEndpointsRequireAuthentication() throws Exception {
        error(get("/api/admin/pages"), 401, "AUTH_REQUIRED");
        error(jsonBody(put("/api/admin/pages/about"), Map.of("title", "X")), 401, "AUTH_REQUIRED");
        error(get("/api/admin/faqs"), 401, "AUTH_REQUIRED");
        error(jsonBody(post("/api/admin/faqs"), faq("Q", "<p>A</p>", 0, "ACTIVE")), 401, "AUTH_REQUIRED");
    }

    @Test
    void publicFaqsAreActiveOnlyAndSortedBySortOrder() throws Exception {
        String prefix = unique("Câu hỏi");
        String second = createFaq(faq(prefix + " 2", "<p>Hai</p>", 2, "ACTIVE"));
        String first = createFaq(faq(prefix + " 1", "<p>Một</p><script>x</script>", 1, null));
        String hidden = createFaq(faq(prefix + " ẩn", "<p>Ẩn</p>", 0, "INACTIVE"));
        String deleted = createFaq(faq(prefix + " xoá", "<p>Xoá</p>", 0, "ACTIVE"));
        mvc.perform(admin(delete("/api/admin/faqs/" + deleted))).andExpect(status().isOk());

        List<String> publicIds = faqIds(publicData("/api/faqs?limit=100"));
        assertTrue(publicIds.containsAll(List.of(first, second)), publicIds.toString());
        assertTrue(publicIds.indexOf(first) < publicIds.indexOf(second), publicIds.toString());
        assertFalse(publicIds.contains(hidden), "INACTIVE FAQ is hidden");
        assertFalse(publicIds.contains(deleted), "deleted FAQ is hidden");

        JsonNode created = adminData("/api/admin/faqs/" + first);
        assertEquals("ACTIVE", created.get("status").asText(), "status defaults to ACTIVE");
        assertEquals("<p>Một</p>", created.get("answerHtml").asText());
        assertEquals(1, created.get("sortOrder").asInt());

        // Admin sees INACTIVE ones and can filter by status and keyword.
        assertEquals(List.of(hidden, first, second), faqIds(adminData("/api/admin/faqs?limit=100&keyword=" + prefix)));
        assertEquals(List.of(hidden), faqIds(adminData("/api/admin/faqs?status=inactive&keyword=" + prefix)));

        mvc.perform(admin(jsonBody(put("/api/admin/faqs/" + hidden), faq(prefix + " hiện", "<p>Hiện</p>", 3, "ACTIVE"))))
                .andExpect(status().isOk());
        assertTrue(faqIds(publicData("/api/faqs?limit=100")).contains(hidden));

        error(admin(get("/api/admin/faqs/" + deleted)), 404, "FAQ_NOT_FOUND");
        error(admin(get("/api/admin/faqs/" + UUID.randomUUID())), 404, "FAQ_NOT_FOUND");
        error(admin(jsonBody(put("/api/admin/faqs/" + UUID.randomUUID()), faq("Q", "<p>A</p>", 0, null))), 404, "FAQ_NOT_FOUND");
        error(admin(delete("/api/admin/faqs/" + UUID.randomUUID())), 404, "FAQ_NOT_FOUND");
    }

    @Test
    void faqRequiresQuestionAndAnswer() throws Exception {
        error(admin(jsonBody(post("/api/admin/faqs"), faq(" ", "<p>A</p>", 0, null))), 400, "VALIDATION_ERROR");
        error(admin(jsonBody(post("/api/admin/faqs"), faq("Q", " ", 0, null))), 400, "VALIDATION_ERROR");
        error(admin(jsonBody(post("/api/admin/faqs"), faq("Q", "<script>alert(1)</script>", 0, null))), 400, "VALIDATION_ERROR");
        error(admin(jsonBody(post("/api/admin/faqs"), faq("Q", "<p>A</p>", 0, "DRAFT"))), 400, "VALIDATION_ERROR");
        error(get("/api/faqs?limit=101"), 400, "VALIDATION_ERROR");
    }

    @Test
    void imagesUsedInPagesAndFaqsStayReferenced() throws Exception {
        JsonNode image = uploadImage(TestImages.png(10, 10, false), "content.png");
        String img = "<p>x</p><img src=\"" + image.get("url").asText() + "\">";

        // The image is shared with a blog post: removing it from the post must not release it while the page and FAQ use it.
        String postId = createId("/api/admin/blogs", Map.of("title", unique("Post"), "contentHtml", img));
        mvc.perform(admin(jsonBody(put("/api/admin/pages/purchase-shipping"), Map.of("title", "Mua hàng", "contentHtml", img))))
                .andExpect(status().isOk());
        String faqId = createFaq(faq(unique("Ảnh"), img, 0, "ACTIVE"));
        mvc.perform(admin(delete("/api/admin/blogs/" + postId))).andExpect(status().isOk());
        assertEquals("ACTIVE", fileStatus(image));

        mvc.perform(admin(jsonBody(put("/api/admin/pages/purchase-shipping"), Map.of("title", "Mua hàng", "contentHtml", "<p>x</p>"))))
                .andExpect(status().isOk());
        assertEquals("ACTIVE", fileStatus(image), "still used by the FAQ");

        mvc.perform(admin(delete("/api/admin/faqs/" + faqId))).andExpect(status().isOk());
        assertEquals("INACTIVE", fileStatus(image));
    }

    private static Map<String, Object> faq(String question, String answerHtml, int sortOrder, String status) {
        Map<String, Object> body = new HashMap<>(Map.of("question", question, "answerHtml", answerHtml, "sortOrder", sortOrder));
        if (status != null) body.put("status", status);
        return body;
    }

    private String createFaq(Map<String, Object> body) throws Exception {
        String id = createId("/api/admin/faqs", body);
        Thread.sleep(5); // distinct createdAt, so the order of equal sortOrder values is deterministic
        return id;
    }

    private static List<String> faqIds(JsonNode page) {
        List<String> ids = new ArrayList<>();
        page.get("items").forEach(item -> ids.add(item.get("id").asText()));
        return ids;
    }

    private String fileStatus(JsonNode upload) {
        return mongo.findById(UUID.fromString(upload.get("fileId").asText()), FileDocument.class).status();
    }

    private String createId(String url, Object payload) throws Exception {
        return body(mvc.perform(admin(jsonBody(post(url), payload))).andExpect(status().isOk()).andReturn()).get("data").get("id").asText();
    }

    private JsonNode publicData(String url) throws Exception {
        return body(mvc.perform(get(url)).andExpect(status().isOk()).andReturn()).get("data");
    }

    private JsonNode adminData(String url) throws Exception {
        return body(mvc.perform(admin(get(url))).andExpect(status().isOk()).andReturn()).get("data");
    }

    private JsonNode error(MockHttpServletRequestBuilder request, int status, String code) throws Exception {
        JsonNode body = body(mvc.perform(request).andExpect(status().is(status)).andReturn());
        assertFalse(body.get("success").asBoolean(), body.toString());
        assertEquals(code, body.get("code").asText(), body.toString());
        assertFalse(body.get("message").asText().isBlank(), body.toString());
        return body;
    }
}
