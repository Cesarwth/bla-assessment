package com.ballastlane.assessment.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.ballastlane.assessment.application.port.out.PokeApiGateway;
import com.ballastlane.assessment.domain.PokemonDetail;
import com.ballastlane.assessment.domain.exception.InvalidPokemonIdentifierException;
import com.ballastlane.assessment.domain.exception.PokemonNotFoundException;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GetPokemonDetailTest {

    private static final PokemonDetail PIKACHU = new PokemonDetail(25, "pikachu", "img", "sprite", List.of("electric"),
            6.0, 0.4, List.of("static"), List.of(), "Mouse Pokémon", "desc", null);

    @Mock PokeApiGateway gateway;
    GetPokemonDetail getPokemonDetail;

    @BeforeEach
    void setUp() {
        getPokemonDetail = new GetPokemonDetail(gateway);
    }

    @Test
    void returnsTheDetailFromTheGateway() {
        when(gateway.findDetail("25")).thenReturn(Optional.of(PIKACHU));

        assertThat(getPokemonDetail.get("25")).isSameAs(PIKACHU);
    }

    @Test
    void trimsAndLowercasesTheIdentifier() {
        when(gateway.findDetail("mr-mime")).thenReturn(Optional.of(PIKACHU));

        getPokemonDetail.get("  Mr-Mime ");

        verify(gateway).findDetail("mr-mime");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "../admin", "pika chu", "a/b", "pikachu?x=1", "pika%20"})
    void rejectsBlankOrUnsafeIdentifiersWithoutCallingUpstream(String identifier) {
        assertThatThrownBy(() -> getPokemonDetail.get(identifier))
                .isInstanceOf(InvalidPokemonIdentifierException.class);
        verifyNoInteractions(gateway);
    }

    @Test
    void throwsNotFoundWhenTheGatewayHasNothing() {
        when(gateway.findDetail("missingno")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> getPokemonDetail.get("missingno")).isInstanceOf(PokemonNotFoundException.class);
    }
}
