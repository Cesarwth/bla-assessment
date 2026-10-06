package com.ballastlane.assessment.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class PageResultTest {

    @Test
    void computesTotalPagesRoundingUp() {
        assertThat(new PageResult<>(List.of(1, 2, 3), 0, 3, 7).totalPages()).isEqualTo(3);
        assertThat(new PageResult<>(List.of(1, 2, 3), 0, 3, 6).totalPages()).isEqualTo(2);
    }

    @Test
    void emptyResultHasZeroPages() {
        assertThat(new PageResult<Integer>(List.of(), 0, 12, 0).totalPages()).isZero();
    }
}
