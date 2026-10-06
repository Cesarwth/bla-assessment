package com.ballastlane.assessment.infrastructure.pokeapi;

/** Cache names live in one place so annotations and the cache manager cannot drift apart. */
final class PokeApiCaches {

    static final String PAGES = "pokeapi-pages";
    static final String POKEMON = "pokeapi-pokemon";
    static final String SPECIES = "pokeapi-species";
    static final String EVOLUTION_CHAINS = "pokeapi-evolution-chains";

    private PokeApiCaches() {
    }
}
