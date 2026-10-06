package com.ballastlane.assessment.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ballastlane.assessment.domain.exception.InvalidPokemonIdentifierException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class PokemonIdentifierTest {

    @Test
    void normalizesCaseAndWhitespace() {
        assertThat(PokemonIdentifier.of("  Mr-Mime ").value()).isEqualTo("mr-mime");
        assertThat(PokemonIdentifier.of("25").value()).isEqualTo("25");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "../admin", "pika chu", "a/b", "pikachu?x=1", "pika%20", "pika!"})
    void rejectsBlankAndUnsafeValues(String raw) {
        assertThatThrownBy(() -> PokemonIdentifier.of(raw)).isInstanceOf(InvalidPokemonIdentifierException.class);
    }

    @Test
    void numericIdsMustBePositive() {
        assertThat(PokemonIdentifier.requirePositive(25)).isEqualTo(25);
        assertThatThrownBy(() -> PokemonIdentifier.requirePositive(0)).isInstanceOf(InvalidPokemonIdentifierException.class);
        assertThatThrownBy(() -> PokemonIdentifier.requirePositive(-5)).isInstanceOf(InvalidPokemonIdentifierException.class);
    }
}
