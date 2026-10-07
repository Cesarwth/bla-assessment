package com.ballastlane.assessment.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ballastlane.assessment.domain.exception.InvalidLocalAttributesException;
import java.util.List;
import org.junit.jupiter.api.Test;

class LocalAttributesTest {

    @Test
    void emptyHasNothingSet() {
        LocalAttributes empty = LocalAttributes.empty();

        assertThat(empty.nickname()).isNull();
        assertThat(empty.region()).isNull();
        assertThat(empty.tags()).isEmpty();
    }

    @Test
    void trimsTextAndTurnsBlankIntoNull() {
        LocalAttributes attributes = new LocalAttributes("  Pika  ", "   ", null);

        assertThat(attributes.nickname()).isEqualTo("Pika");
        assertThat(attributes.region()).isNull();
        assertThat(attributes.tags()).isEmpty();
    }

    @Test
    void trimsAndLowercasesTags() {
        assertThat(new LocalAttributes(null, null, List.of("  Starter ", "ELECTRIC")).tags())
                .containsExactly("starter", "electric");
    }

    @Test
    void dedupesTagsKeepingFirstOccurrence() {
        assertThat(new LocalAttributes(null, null, List.of("a", "B", "b", "A", "c")).tags())
                .containsExactly("a", "b", "c");
    }

    @Test
    void rejectsTooLongNicknameOrRegion() {
        assertThatThrownBy(() -> new LocalAttributes("x".repeat(51), null, List.of()))
                .isInstanceOf(InvalidLocalAttributesException.class);
        assertThatThrownBy(() -> new LocalAttributes(null, "x".repeat(51), List.of()))
                .isInstanceOf(InvalidLocalAttributesException.class);
        assertThat(new LocalAttributes("x".repeat(50), null, List.of()).nickname()).hasSize(50);
    }

    @Test
    void rejectsTooManyTagsAfterDedupe() {
        List<String> eleven = java.util.stream.IntStream.range(0, 11).mapToObj(index -> "tag" + index).toList();

        assertThatThrownBy(() -> new LocalAttributes(null, null, eleven)).isInstanceOf(InvalidLocalAttributesException.class);
        assertThat(new LocalAttributes(null, null, eleven.subList(0, 10)).tags()).hasSize(10);
    }

    @Test
    void rejectsBlankOrTooLongTags() {
        assertThatThrownBy(() -> new LocalAttributes(null, null, List.of("ok", "  ")))
                .isInstanceOf(InvalidLocalAttributesException.class);
        assertThatThrownBy(() -> new LocalAttributes(null, null, List.of("x".repeat(31))))
                .isInstanceOf(InvalidLocalAttributesException.class);
    }
}
