package com.fss.backend.content.html;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HtmlSanitizerServiceTest {
    private final HtmlSanitizerService sanitizer = new HtmlSanitizerService("", "/files");

    @Test
    void sanitizeShouldRemoveScriptTags() {
        String html = sanitizer.sanitize("<p>Hello</p><script>alert(1)</script>");

        assertTrue(html.contains("<p>Hello</p>"));
        assertFalse(html.contains("<script"));
        assertFalse(html.contains("alert(1)"));
    }

    @Test
    void sanitizeShouldRemoveEventHandlers() {
        String html = sanitizer.sanitize("<img src=\"/files/2026-05-06/a.png\" onerror=\"alert(1)\">");

        assertTrue(html.contains("src=\"/files/2026-05-06/a.png\""));
        assertFalse(html.contains("onerror"));
    }

    @Test
    void sanitizeShouldRemoveJavascriptHref() {
        String html = sanitizer.sanitize("<a href=\"javascript:alert(1)\">x</a>");

        assertTrue(html.contains("<a"));
        assertFalse(html.contains("javascript:"));
        assertFalse(html.contains("href="));
    }

    @Test
    void sanitizeShouldAllowCommonTinyMceTagsAndLocalImages() {
        String html = sanitizer.sanitize("<p>Mô tả <strong>sản phẩm</strong></p><ul><li>Êm chân</li></ul>"
                + "<img src=\"/files/2026-05-06/image.png\" alt=\"\">");

        assertTrue(html.contains("<p>"));
        assertTrue(html.contains("<strong>sản phẩm</strong>"));
        assertTrue(html.contains("<ul>"));
        assertTrue(html.contains("<li>Êm chân</li>"));
        assertTrue(html.contains("src=\"/files/2026-05-06/image.png\""));
    }

    @Test
    void sanitizeShouldAllowAbsoluteLocalImageUrlsWithSpaces() {
        HtmlSanitizerService localSanitizer = new HtmlSanitizerService("localhost", "/files");

        String html = localSanitizer.sanitize("""
                <p><img src="http://localhost:8080/files/2026-07-27/c60b717b-Screenshot from 2026-07-23.png"
                        alt="" width="206" height="94"></p>
                """);

        assertTrue(html.contains("<img"));
        assertTrue(html.contains("src=\"http://localhost:8080/files/2026-07-27/c60b717b-Screenshot from 2026-07-23.png\""));
        assertTrue(html.contains("width=\"206\""));
        assertTrue(html.contains("height=\"94\""));
    }

    @Test
    void sanitizeShouldAllowAbsoluteLocalImageUrlsWithEncodedSpaces() {
        HtmlSanitizerService localSanitizer = new HtmlSanitizerService("localhost", "/files");

        String html = localSanitizer.sanitize("""
                <p><img src="http://localhost:8080/files/2026-07-27/c60b717b-Screenshot%20from%202026-07-23.png"></p>
                """);

        assertTrue(html.contains("<img"));
        assertTrue(html.contains("src=\"http://localhost:8080/files/2026-07-27/c60b717b-Screenshot%20from%202026-07-23.png\""));
    }

    @Test
    void sanitizeShouldRemoveUnsupportedImageSources() {
        String html = sanitizer.sanitize("<img src=\"data:image/png;base64,abc\"><img src=\"https://evil.example/a.png\">");

        assertFalse(html.contains("<img"));
    }

    @Test
    void sanitizeShouldAllowBasicInlineStyles() {
        String html = sanitizer.sanitize("""
                <p style="color: #ff0000; background: rgb(1, 2, 3); text-align: center;
                          font-size: 16px; font-family: Arial, 'Helvetica Neue'; font-weight: bold;
                          font-style: italic; text-decoration: underline;">Text</p>
                """);

        assertTrue(html.contains("color: #ff0000;"));
        assertTrue(html.contains("background-color: rgb(1, 2, 3);"));
        assertTrue(html.contains("text-align: center;"));
        assertTrue(html.contains("font-size: 16px;"));
        assertTrue(html.contains("font-family: Arial, 'Helvetica Neue';"));
        assertTrue(html.contains("font-weight: bold;"));
        assertTrue(html.contains("font-style: italic;"));
        assertTrue(html.contains("text-decoration: underline;"));
    }

    @Test
    void sanitizeShouldRemoveUnsafeInlineStyles() {
        String html = sanitizer.sanitize("""
                <p style="background: url(javascript:alert(1)); position: fixed; color: red;">Text</p>
                """);

        assertTrue(html.contains("color: red;"));
        assertFalse(html.contains("url("));
        assertFalse(html.contains("javascript:"));
        assertFalse(html.contains("position"));
    }
}
