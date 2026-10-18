package com.hireflow.api.dto;

import org.springframework.data.domain.Page;

import java.util.Collections;
import java.util.List;

/**
 * Standardized envelope for paginated collection responses.
 */
public record PagedResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean last
) {
    public PagedResponse {
        content = content != null ? Collections.unmodifiableList(content) : Collections.emptyList();
    }

    public static <T> PagedResponse<T> fromPage(Page<T> page) {
        if (page == null) {
            return new PagedResponse<>(Collections.emptyList(), 0, 0, 0, 0, true);
        }
        return new PagedResponse<>(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.isLast()
        );
    }
}
