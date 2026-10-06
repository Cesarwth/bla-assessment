package com.ballastlane.assessment.application.usecase;

import com.ballastlane.assessment.domain.exception.InvalidPaginationException;

/** One definition of page defaults and limits, shared by the catalog and the local collection. */
record Pagination(int page, int size) {

    static final int DEFAULT_SIZE = 12;
    // Catalog rows cost one upstream call each, so the cap protects PokeAPI; the collection reuses it for consistency.
    static final int MAX_SIZE = 50;

    static Pagination of(Integer page, Integer size) {
        int validPage = page == null ? 0 : page;
        int validSize = size == null ? DEFAULT_SIZE : size;
        if (validPage < 0) {
            throw new InvalidPaginationException("page must be 0 or greater");
        }
        if (validSize < 1 || validSize > MAX_SIZE) {
            throw new InvalidPaginationException("size must be between 1 and " + MAX_SIZE);
        }
        return new Pagination(validPage, validSize);
    }
}
