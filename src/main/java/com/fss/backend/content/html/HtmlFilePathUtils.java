package com.fss.backend.content.html;

import java.net.URI;
import java.util.Set;

final class HtmlFilePathUtils {
    private static final String FILE_PREFIX = "/files/";

    private HtmlFilePathUtils() {
    }

    static String localFilePath(String src) {
        return localFilePath(src, null);
    }

    static String localFilePath(String src, Set<String> allowedHosts) {
        if (src == null || src.isBlank() || src.startsWith("data:")) {
            return null;
        }

        try {
            URI uri = URI.create(normalizeUriText(src.trim()));
            String scheme = uri.getScheme();
            if (scheme == null && uri.getRawAuthority() == null) {
                return pathWithoutFilesPrefix(uri.getPath());
            }
            if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) {
                return null;
            }
            String host = uri.getHost() == null ? null : uri.getHost().toLowerCase();
            if (allowedHosts != null && !allowedHosts.contains(host)) {
                return null;
            }
            return pathWithoutFilesPrefix(uri.getPath());
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private static String pathWithoutFilesPrefix(String path) {
        if (path == null || !path.startsWith(FILE_PREFIX)) {
            return null;
        }
        return path.substring(FILE_PREFIX.length());
    }

    private static String normalizeUriText(String value) {
        StringBuilder normalized = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char current = value.charAt(i);
            if (current == ' ') {
                normalized.append("%20");
            } else if (current == '%' && !isEscapedPercent(value, i)) {
                normalized.append("%25");
            } else {
                normalized.append(current);
            }
        }
        return normalized.toString();
    }

    private static boolean isEscapedPercent(String value, int index) {
        return index + 2 < value.length()
                && isHex(value.charAt(index + 1))
                && isHex(value.charAt(index + 2));
    }

    private static boolean isHex(char value) {
        return (value >= '0' && value <= '9')
                || (value >= 'a' && value <= 'f')
                || (value >= 'A' && value <= 'F');
    }
}
