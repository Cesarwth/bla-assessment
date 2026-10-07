package com.ballastlane.assessment.infrastructure.pokeapi;

import com.ballastlane.assessment.infrastructure.pokeapi.dto.PokeApiEvolutionChain;
import com.ballastlane.assessment.infrastructure.pokeapi.dto.PokeApiPage;
import com.ballastlane.assessment.infrastructure.pokeapi.dto.PokeApiPokemon;
import com.ballastlane.assessment.infrastructure.pokeapi.dto.PokeApiSpecies;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import tools.jackson.databind.json.JsonMapper;

/** JSON captured from the real PokeAPI (trimmed); tests never touch the network. */
final class Fixtures {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private Fixtures() {
    }

    static PokeApiPokemon pokemon(String name) {
        return JSON.readValue(read(name), PokeApiPokemon.class);
    }

    static PokeApiSpecies species(String name) {
        return JSON.readValue(read(name), PokeApiSpecies.class);
    }

    static PokeApiEvolutionChain chain(String name) {
        return JSON.readValue(read(name), PokeApiEvolutionChain.class);
    }

    static PokeApiPage page(String name) {
        return JSON.readValue(read(name), PokeApiPage.class);
    }

    static String read(String name) {
        try (InputStream in = Fixtures.class.getResourceAsStream("/pokeapi/" + name)) {
            if (in == null) {
                throw new IllegalArgumentException("Missing fixture " + name);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }
}
