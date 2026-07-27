package com.fss.backend.content.html;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.safety.Cleaner;
import org.jsoup.safety.Safelist;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.util.HashSet;
import java.util.Set;

@Service
public class HtmlSanitizerService {
    private static final Set<String> SAFE_LINK_SCHEMES = Set.of("http", "https", "mailto", "tel");
    private static final Set<String> STYLE_TAGS = Set.of("p", "span", "strong", "b", "em", "i", "u", "s",
            "h1", "h2", "h3", "h4", "ul", "ol", "li", "blockquote", "a",
            "table", "thead", "tbody", "tr", "th", "td", "code", "pre", "figure", "figcaption");
    private static final Set<String> ALLOWED_STYLE_PROPERTIES = Set.of("color", "background", "background-color",
            "text-align", "font-size", "font-family", "font-weight", "font-style", "text-decoration",
            "text-decoration-line");
    private static final Set<String> TEXT_ALIGN_VALUES = Set.of("left", "right", "center", "justify", "start", "end");
    private static final Set<String> FONT_WEIGHT_VALUES = Set.of("normal", "bold", "bolder", "lighter",
            "100", "200", "300", "400", "500", "600", "700", "800", "900");
    private static final Set<String> FONT_STYLE_VALUES = Set.of("normal", "italic", "oblique");
    private static final Set<String> TEXT_DECORATION_VALUES = Set.of("none", "underline", "line-through", "overline");
    private static final Set<String> FONT_SIZE_KEYWORDS = Set.of("xx-small", "x-small", "small", "medium",
            "large", "x-large", "xx-large", "smaller", "larger");

    private final Safelist safelist;
    private final Set<String> allowedImageHosts;

    public HtmlSanitizerService(@Value("${app.content.allowed-image-hosts:localhost,127.0.0.1}") String allowedImageHosts,
                                @Value("${app.cdn.base-url:/files}") String cdnBaseUrl) {
        this.allowedImageHosts = parseHosts(allowedImageHosts);
        addHostFromUrl(cdnBaseUrl, this.allowedImageHosts);
        this.safelist = new Safelist()
                .addTags("p", "br", "strong", "b", "em", "i", "u", "s",
                        "h1", "h2", "h3", "h4", "ul", "ol", "li", "blockquote",
                        "a", "img", "table", "thead", "tbody", "tr", "th", "td",
                        "code", "pre", "figure", "figcaption")
                .addAttributes("a", "href", "title", "target")
                .addAttributes("img", "src", "alt", "title", "width", "height")
                .addAttributes("th", "colspan", "rowspan")
                .addAttributes("td", "colspan", "rowspan")
                .addProtocols("a", "href", "http", "https", "mailto", "tel")
                .addEnforcedAttribute("a", "rel", "noopener noreferrer")
                .preserveRelativeLinks(true);
        STYLE_TAGS.forEach(tag -> this.safelist.addAttributes(tag, "style"));
    }

    public String sanitize(String html) {
        if (html == null || html.isBlank()) {
            return null;
        }

        Document dirty = Jsoup.parseBodyFragment(html);
        Document clean = new Cleaner(safelist).clean(dirty);
        clean.outputSettings().prettyPrint(false);
        clean.select("img").forEach(this::removeUnsafeImage);
        clean.select("a[href]").forEach(this::removeUnsafeLink);
        clean.select("a[target=_blank]").attr("rel", "noopener noreferrer");
        clean.select("[style]").forEach(this::sanitizeStyle);
        return clean.body().html();
    }

    private void sanitizeStyle(Element element) {
        String sanitized = sanitizeStyleAttribute(element.attr("style"));
        if (sanitized.isBlank()) {
            element.removeAttr("style");
        } else {
            element.attr("style", sanitized);
        }
    }

    private String sanitizeStyleAttribute(String style) {
        if (style == null || style.isBlank()) {
            return "";
        }
        StringBuilder result = new StringBuilder();
        for (String declaration : style.split(";")) {
            int colon = declaration.indexOf(':');
            if (colon <= 0) {
                continue;
            }
            String property = declaration.substring(0, colon).trim().toLowerCase();
            String value = declaration.substring(colon + 1).trim();
            String safeValue = safeStyleValue(property, value);
            if (safeValue == null) {
                continue;
            }
            if (!result.isEmpty()) {
                result.append(' ');
            }
            result.append("background".equals(property) ? "background-color" : property)
                    .append(": ")
                    .append(safeValue)
                    .append(';');
        }
        return result.toString();
    }

    private String safeStyleValue(String property, String value) {
        if (!ALLOWED_STYLE_PROPERTIES.contains(property) || value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim();
        String lower = normalized.toLowerCase();
        if (lower.contains("url(") || lower.contains("expression(") || lower.contains("@import")
                || lower.contains("javascript:") || lower.contains("data:") || lower.contains("<")) {
            return null;
        }
        return switch (property) {
            case "color", "background", "background-color" -> isSafeColor(normalized) ? normalized : null;
            case "text-align" -> TEXT_ALIGN_VALUES.contains(lower) ? lower : null;
            case "font-size" -> isSafeFontSize(lower) ? lower : null;
            case "font-family" -> isSafeFontFamily(normalized) ? normalized : null;
            case "font-weight" -> FONT_WEIGHT_VALUES.contains(lower) ? lower : null;
            case "font-style" -> FONT_STYLE_VALUES.contains(lower) ? lower : null;
            case "text-decoration", "text-decoration-line" -> isSafeTextDecoration(lower) ? lower : null;
            default -> null;
        };
    }

    private boolean isSafeColor(String value) {
        String lower = value.toLowerCase();
        return lower.matches("#[0-9a-f]{3}([0-9a-f]{3})?([0-9a-f]{2})?")
                || lower.matches("rgba?\\(\\s*\\d{1,3}\\s*,\\s*\\d{1,3}\\s*,\\s*\\d{1,3}\\s*(,\\s*(0|1|0?\\.\\d+)\\s*)?\\)")
                || lower.matches("hsla?\\(\\s*\\d{1,3}\\s*,\\s*\\d{1,3}%\\s*,\\s*\\d{1,3}%\\s*(,\\s*(0|1|0?\\.\\d+)\\s*)?\\)")
                || lower.matches("[a-z]{3,20}");
    }

    private boolean isSafeFontSize(String value) {
        return FONT_SIZE_KEYWORDS.contains(value)
                || value.matches("(0|[1-9]\\d{0,2})(\\.\\d{1,2})?(px|em|rem|%)");
    }

    private boolean isSafeFontFamily(String value) {
        return value.length() <= 160 && value.matches("[\\p{L}\\p{N}\\s,'\"\\-]+");
    }

    private boolean isSafeTextDecoration(String value) {
        for (String token : value.split("\\s+")) {
            if (!TEXT_DECORATION_VALUES.contains(token)) {
                return false;
            }
        }
        return true;
    }

    private void removeUnsafeImage(Element image) {
        String src = image.attr("src");
        if (!isAllowedImageSrc(src)) {
            image.remove();
        }
    }

    private boolean isAllowedImageSrc(String src) {
        return HtmlFilePathUtils.localFilePath(src, allowedImageHosts) != null;
    }

    private void removeUnsafeLink(Element link) {
        String href = link.attr("href");
        if (href == null || href.isBlank() || href.startsWith("#") || href.startsWith("/")) {
            return;
        }
        try {
            URI uri = URI.create(href);
            if (uri.getScheme() != null && SAFE_LINK_SCHEMES.contains(uri.getScheme().toLowerCase())) {
                return;
            }
        } catch (IllegalArgumentException ignored) {
            // Drop malformed href values below.
        }
        link.removeAttr("href");
    }

    private Set<String> parseHosts(String hosts) {
        Set<String> result = new HashSet<>();
        if (hosts == null || hosts.isBlank()) {
            return result;
        }
        for (String host : hosts.split(",")) {
            String normalized = host.trim().toLowerCase();
            if (!normalized.isBlank()) {
                result.add(normalized);
            }
        }
        return result;
    }

    private void addHostFromUrl(String url, Set<String> hosts) {
        if (url == null || url.isBlank() || url.startsWith("/")) {
            return;
        }
        try {
            String host = URI.create(url).getHost();
            if (host != null && !host.isBlank()) {
                hosts.add(host.toLowerCase());
            }
        } catch (IllegalArgumentException ignored) {
            // Invalid config should not disable local /files sanitizing.
        }
    }
}
