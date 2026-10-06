package com.ballastlane.assessment.domain;

import java.util.List;

/** Framework-neutral page; Spring Data's Page must not reach the domain. */
public record PageResult<T>(List<T> items, int page, int size, long totalItems) {

    public PageResult {
        items = List.copyOf(items);
    }

    public int totalPages() {
        return size <= 0 ? 0 : (int) ((totalItems + size - 1) / size);
    }
}
