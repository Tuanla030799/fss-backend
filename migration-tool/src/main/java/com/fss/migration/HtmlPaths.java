package com.fss.migration;

import org.jsoup.Jsoup;

import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Same extraction as the application's HtmlFilePathUtils: {@code <img src=".../files/<path>">} -> {@code <path>}. */
final class HtmlPaths {
    private static final String FILE_PREFIX = "/files/";

    private HtmlPaths() {
    }

    static List<String> extract(String html) {
        if (html == null || html.isBlank()) return List.of();
        Set<String> paths = new LinkedHashSet<>();
        Jsoup.parseBodyFragment(html).select("img[src]").forEach(image -> {
            String path = localFilePath(image.attr("src"));
            if (path != null && !path.isBlank()) paths.add(path);
        });
        return new ArrayList<>(paths);
    }

    static String localFilePath(String src) {
        if (src == null || src.isBlank() || src.startsWith("data:")) return null;
        try {
            URI uri = URI.create(normalize(src.trim()));
            String scheme = uri.getScheme();
            boolean relative = scheme == null && uri.getRawAuthority() == null;
            if (!relative && !"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) return null;
            String path = uri.getPath();
            return path == null || !path.startsWith(FILE_PREFIX) ? null : path.substring(FILE_PREFIX.length());
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private static String normalize(String value) {
        StringBuilder out = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == ' ') {
                out.append("%20");
            } else if (c == '%' && !(i + 2 < value.length() && isHex(value.charAt(i + 1)) && isHex(value.charAt(i + 2)))) {
                out.append("%25");
            } else {
                out.append(c);
            }
        }
        return out.toString();
    }

    private static boolean isHex(char c) {
        return (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F');
    }
}
