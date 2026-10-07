package com.ballastlane.assessment.infrastructure.pokeapi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ballastlane.assessment.domain.EvolutionNode;
import com.ballastlane.assessment.domain.PokemonDetail;
import com.ballastlane.assessment.domain.PokemonSummary;
import com.ballastlane.assessment.domain.exception.CatalogUnavailableException;
import com.ballastlane.assessment.infrastructure.pokeapi.dto.NamedRef;
import com.ballastlane.assessment.infrastructure.pokeapi.dto.PokeApiEvolutionChain;
import com.ballastlane.assessment.infrastructure.pokeapi.dto.PokeApiEvolutionChain.ChainLink;
import com.ballastlane.assessment.infrastructure.pokeapi.dto.PokeApiEvolutionChain.EvolutionDetail;
import com.ballastlane.assessment.infrastructure.pokeapi.dto.PokeApiPokemon;
import com.ballastlane.assessment.infrastructure.pokeapi.dto.PokeApiPokemon.Artwork;
import com.ballastlane.assessment.infrastructure.pokeapi.dto.PokeApiPokemon.Other;
import com.ballastlane.assessment.infrastructure.pokeapi.dto.PokeApiPokemon.Sprites;
import com.ballastlane.assessment.infrastructure.pokeapi.dto.PokeApiSpecies;
import com.ballastlane.assessment.infrastructure.pokeapi.dto.PokeApiSpecies.FlavorText;
import java.util.List;
import org.junit.jupiter.api.Test;

class PokeApiMapperTest {

    private final PokeApiMapper mapper = new PokeApiMapper();

    private static PokeApiPokemon pokemonWithSprites(Sprites sprites) {
        return new PokeApiPokemon(1, "bulbasaur", 7, 69, List.of(), List.of(), List.of(), sprites,
                new NamedRef("bulbasaur", "https://pokeapi.co/api/v2/pokemon-species/1/"));
    }

    private static EvolutionDetail detail(String trigger, String itemName, Integer level, Integer happiness, String time) {
        return new EvolutionDetail(new NamedRef(trigger, null), itemName == null ? null : new NamedRef(itemName, null), level, happiness, time);
    }

    private static ChainLink link(String name, int id, List<EvolutionDetail> details, ChainLink... next) {
        return new ChainLink(new NamedRef(name, "https://pokeapi.co/api/v2/pokemon-species/" + id + "/"), details, List.of(next));
    }

    private static EvolutionNode child(EvolutionNode node, int index) {
        return node.evolvesTo().get(index);
    }

    @Test
    void summaryConvertsUnitsAndFlattensNames() {
        PokemonSummary summary = mapper.toSummary(Fixtures.pokemon("pokemon-25.json"));

        assertThat(summary.id()).isEqualTo(25);
        assertThat(summary.name()).isEqualTo("pikachu");
        assertThat(summary.weightKg()).isEqualTo(6.0);
        assertThat(summary.types()).containsExactly("electric");
        assertThat(summary.abilities()).containsExactly("static", "lightning-rod");
        assertThat(summary.spriteUrl()).endsWith("/pokemon/25.png");
        assertThat(summary.imageUrl()).endsWith("/official-artwork/25.png");
    }

    @Test
    void summaryImageFallsBackToTheSpriteOrIsNullWhenThereIsNoArtwork() {
        PokemonSummary noArtwork = mapper.toSummary(
                pokemonWithSprites(new Sprites("sprite.png", new Other(new Artwork(null)))));
        PokemonSummary noSprites = mapper.toSummary(pokemonWithSprites(new Sprites(null, null)));
        PokemonSummary noSpritesAtAll = mapper.toSummary(pokemonWithSprites(null));

        assertThat(noArtwork.imageUrl()).isEqualTo("sprite.png");
        assertThat(noSprites.imageUrl()).isNull();
        assertThat(noSpritesAtAll.imageUrl()).isNull();
    }

    @Test
    void detailPrefersOfficialArtworkAndKeepsStatsInOrder() {
        PokemonDetail detail = mapper.toDetail(Fixtures.pokemon("pokemon-25.json"), null, null);

        assertThat(detail.imageUrl()).endsWith("/official-artwork/25.png");
        assertThat(detail.heightM()).isEqualTo(0.4);
        assertThat(detail.stats()).extracting("name").containsExactly(
                "hp", "attack", "defense", "special-attack", "special-defense", "speed");
        assertThat(detail.stats().get(5).baseValue()).isEqualTo(90);
    }

    @Test
    void imageFallsBackToTheSpriteWhenThereIsNoArtwork() {
        PokemonDetail detail = mapper.toDetail(
                pokemonWithSprites(new Sprites("sprite.png", new Other(new Artwork(null)))), null, null);

        assertThat(detail.imageUrl()).isEqualTo("sprite.png");
    }

    @Test
    void missingSpritesBecomeNullInsteadOfFailing() {
        PokemonDetail detail = mapper.toDetail(pokemonWithSprites(new Sprites(null, null)), null, null);
        PokemonDetail noSpritesAtAll = mapper.toDetail(pokemonWithSprites(null), null, null);

        assertThat(detail.imageUrl()).isNull();
        assertThat(detail.spriteUrl()).isNull();
        assertThat(noSpritesAtAll.imageUrl()).isNull();
    }

    @Test
    void cleansControlCharactersFromTheFlavorText() {
        PokemonDetail detail = mapper.toDetail(Fixtures.pokemon("pokemon-25.json"), Fixtures.species("species-25.json"), null);

        assertThat(detail.description())
                .isEqualTo("When several of these POKéMON gather, their electricity could build and cause lightning storms.");
    }

    @Test
    void picksTheEnglishGenusAndDescription() {
        PokemonDetail detail = mapper.toDetail(Fixtures.pokemon("pokemon-25.json"), Fixtures.species("species-25.json"), null);

        assertThat(detail.genus()).isEqualTo("Mouse Pokémon");
        assertThat(detail.description()).doesNotContain("Levanta");
    }

    @Test
    void descriptionAndGenusAreNullWhenThereIsNoEnglishText() {
        PokeApiSpecies spanishOnly = new PokeApiSpecies(List.of(),
                List.of(new FlavorText("Hola", new NamedRef("es", null))), null);

        PokemonDetail detail = mapper.toDetail(Fixtures.pokemon("pokemon-25.json"), spanishOnly, null);

        assertThat(detail.description()).isNull();
        assertThat(detail.genus()).isNull();
    }

    @Test
    void mapsALinearChainWithRequirements() {
        EvolutionNode pichu = mapper.toEvolutionTree(Fixtures.chain("evolution-10.json"));

        assertThat(pichu.name()).isEqualTo("pichu");
        assertThat(pichu.requirements()).isEmpty();
        EvolutionNode pikachu = child(pichu, 0);
        assertThat(pikachu.id()).isEqualTo(25);
        assertThat(pikachu.requirements()).containsExactly("Level up (happiness 220)");
        EvolutionNode raichu = child(pikachu, 0);
        assertThat(raichu.id()).isEqualTo(26);
        assertThat(raichu.spriteUrl()).endsWith("/sprites/pokemon/26.png");
        assertThat(raichu.evolvesTo()).isEmpty();
    }

    @Test
    void mapsABranchingChain() {
        EvolutionNode eevee = mapper.toEvolutionTree(Fixtures.chain("evolution-67.json"));

        assertThat(eevee.evolvesTo()).extracting(EvolutionNode::name).containsExactly(
                "vaporeon", "jolteon", "flareon", "espeon", "umbreon", "leafeon", "glaceon", "sylveon");
        assertThat(child(eevee, 0).requirements()).containsExactly("Use water-stone");
        assertThat(child(eevee, 3).requirements()).containsExactly("Level up (happiness 160, day)");
    }

    @Test
    void dedupesRepeatedRequirementsKeepingOrder() {
        EvolutionNode eevee = mapper.toEvolutionTree(Fixtures.chain("evolution-67.json"));
        EvolutionNode raichu = child(child(mapper.toEvolutionTree(Fixtures.chain("evolution-10.json")), 0), 0);

        assertThat(child(eevee, 5).requirements()).containsExactly("Level up", "Use leaf-stone");
        assertThat(raichu.requirements()).containsExactly("Use thunder-stone");
    }

    @Test
    void describesTheDifferentTriggers() {
        PokeApiEvolutionChain chain = new PokeApiEvolutionChain(link("a", 1, List.of(),
                link("b", 2, List.of(detail("level-up", null, 16, null, ""))),
                link("c", 3, List.of(detail("trade", null, null, null, ""))),
                link("d", 4, List.of(detail("level-up", null, 36, 70, "night"))),
                link("e", 5, List.of(detail("some-new-trigger", null, null, null, null)))));

        EvolutionNode root = mapper.toEvolutionTree(chain);

        assertThat(child(root, 0).requirements()).containsExactly("Level up (level 16)");
        assertThat(child(root, 1).requirements()).containsExactly("Trade");
        assertThat(child(root, 2).requirements()).containsExactly("Level up (level 36, happiness 70, night)");
        assertThat(child(root, 3).requirements()).containsExactly("Some new trigger");
    }

    @Test
    void aStatWithoutANameIsAnUpstreamProblemNotAServerError() {
        PokeApiPokemon broken = new PokeApiPokemon(1, "x", 1, 1, List.of(), List.of(),
                List.of(new PokeApiPokemon.StatSlot(10, null)), null, new NamedRef("x", "https://pokeapi.co/api/v2/pokemon-species/1/"));

        assertThatThrownBy(() -> mapper.toDetail(broken, null, null)).isInstanceOf(CatalogUnavailableException.class);
    }

    @Test
    void aChainLinkWithoutASpeciesIsAnUpstreamProblemNotAServerError() {
        PokeApiEvolutionChain broken = new PokeApiEvolutionChain(new ChainLink(null, List.of(), List.of()));

        assertThatThrownBy(() -> mapper.toEvolutionTree(broken)).isInstanceOf(CatalogUnavailableException.class);
    }

    @Test
    void idsTooBigToBeRealAreRejectedInsteadOfBlowingUp() {
        assertThatThrownBy(() -> mapper.idFromUrl("https://pokeapi.co/api/v2/pokemon-species/99999999999/"))
                .isInstanceOf(CatalogUnavailableException.class);
    }

    @Test
    void rejectsPayloadsWhoseUrlsDoNotCarryAnId() {
        PokeApiPokemon broken = new PokeApiPokemon(1, "x", 1, 1, List.of(), List.of(), List.of(), null, new NamedRef("x", "not-a-url"));

        assertThatThrownBy(() -> mapper.speciesIdOf(broken)).isInstanceOf(CatalogUnavailableException.class);
    }
}
