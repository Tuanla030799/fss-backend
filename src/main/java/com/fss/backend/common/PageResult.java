package com.fss.backend.common;

import java.util.List;

public record PageResult<T>(List<T> items, int page, int limit, long total, int totalPages) {
    public static <T> PageResult<T> of(List<T> items, int page, int limit, long total) {
        int safePage = Math.max(page, 1);
        int safeLimit = Math.max(limit, 1);
        int pages = total == 0 ? 0 : (int) Math.ceil((double) total / safeLimit);
        return new PageResult<>(items, safePage, safeLimit, total, pages);
    }
}
