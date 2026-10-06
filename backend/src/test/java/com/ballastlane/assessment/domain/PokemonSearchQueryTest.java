package com.ballastlane.assessment.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ballastlane.assessment.domain.exception.InvalidSearchQueryException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class PokemonSearchQueryTest {

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "   ", "\t"})
    void blankOrMissingMeansNoFilter(String raw) {
        assertThat(PokemonSearchQuery.of(raw)).isEmpty();
    }

    @Test
    void trimsAndLowercases() {
        assertThat(PokemonSearchQuery.of("  SaUr ").orElseThrow().text()).isEqualTo("saur");
    }

    @Test
    void spacesBecomeHyphensLikePokeApiNames() {
        assertThat(PokemonSearchQuery.of("Mr  Mime").orElseThrow().text()).isEqualTo("mr-mime");
    }

    @Test
    void specialCharactersAreKeptLiterally() {
        assertThat(PokemonSearchQuery.of("%.*[").orElseThrow().text()).isEqualTo("%.*[");
    }

    @Test
    void allDigitsIsAnIdSearch() {
        PokemonSearchQuery query = PokemonSearchQuery.of(" 025 ").orElseThrow();

        assertThat(query.isId()).isTrue();
        assertThat(query.matches(25, "pikachu")).isTrue();
        assertThat(query.matches(250, "ho-oh")).isFalse();
    }

    @Test
    void anIdTooBigToExistMatchesNothing() {
        PokemonSearchQuery query = PokemonSearchQuery.of("9".repeat(50)).orElseThrow();

        assertThat(query.matches(25, "pikachu")).isFalse();
    }

    @Test
    void zeroNeverMatches() {
        assertThat(PokemonSearchQuery.of("0").orElseThrow().matches(1, "bulbasaur")).isFalse();
    }

    @Test
    void textMatchesNamesBySubstring() {
        PokemonSearchQuery query = PokemonSearchQuery.of("saur").orElseThrow();

        assertThat(query.isId()).isFalse();
        assertThat(query.matches(1, "bulbasaur")).isTrue();
        assertThat(query.matches(25, "pikachu")).isFalse();
    }

    @Test
    void digitsMixedWithLettersAreATextSearch() {
        PokemonSearchQuery query = PokemonSearchQuery.of("porygon2").orElseThrow();

        assertThat(query.isId()).isFalse();
        assertThat(query.matches(233, "porygon2")).isTrue();
    }

    @Test
    void acceptsExactlyTheMaximumLength() {
        assertThat(PokemonSearchQuery.of("a".repeat(PokemonSearchQuery.MAX_LENGTH))).isPresent();
    }

    @Test
    void rejectsLongerThanTheMaximumAfterTrimming() {
        String tooLong = "a".repeat(PokemonSearchQuery.MAX_LENGTH + 1);

        assertThatThrownBy(() -> PokemonSearchQuery.of("  " + tooLong + "  "))
                .isInstanceOf(InvalidSearchQueryException.class)
                .hasMessage("q must be at most 50 characters");
    }
}
