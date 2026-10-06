package com.ballastlane.assessment.infrastructure.web.dto;

import com.ballastlane.assessment.domain.PageResult;
import java.util.List;
import java.util.function.Function;

public record PageResponse<T>(List<T> items, int page, int size, long totalItems, int totalPages) {

    public static <S, T> PageResponse<T> from(PageResult<S> result, Function<S, T> mapper) {
        return new PageResponse<>(result.items().stream().map(mapper).toList(), result.page(), result.size(),
                result.totalItems(), result.totalPages());
    }
}
