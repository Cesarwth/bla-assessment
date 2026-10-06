package com.ballastlane.assessment.infrastructure.pokeapi;

import com.ballastlane.assessment.domain.EvolutionNode;
import com.ballastlane.assessment.domain.PokemonDetail;
import com.ballastlane.assessment.domain.PokemonStat;
import com.ballastlane.assessment.domain.PokemonSummary;
import com.ballastlane.assessment.domain.exception.CatalogUnavailableException;
import com.ballastlane.assessment.infrastructure.pokeapi.dto.NamedRef;
import com.ballastlane.assessment.infrastructure.pokeapi.dto.PokeApiEvolutionChain;
import com.ballastlane.assessment.infrastructure.pokeapi.dto.PokeApiEvolutionChain.ChainLink;
import com.ballastlane.assessment.infrastructure.pokeapi.dto.PokeApiEvolutionChain.EvolutionDetail;
import com.ballastlane.assessment.infrastructure.pokeapi.dto.PokeApiPokemon;
import com.ballastlane.assessment.infrastructure.pokeapi.dto.PokeApiSpecies;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/** The only class that knows PokeAPI's JSON shape: units, nullable fields and text quirks are handled here. */
public class PokeApiMapper {

    private static final Pattern TRAILING_ID = Pattern.compile("/(\\d+)/?$");
    private static final String SPRITE_BASE = "https://raw.githubusercontent.com/PokeAPI/sprites/master/sprites/pokemon/";
    private static final String ENGLISH = "en";

    public PokemonSummary toSummary(PokeApiPokemon pokemon) {
        return new PokemonSummary(
                pokemon.id(),
                pokemon.name(),
                spriteOf(pokemon),
                names(pokemon.types(), slot -> slot.type()),
                toKilograms(pokemon.weight()),
                names(pokemon.abilities(), slot -> slot.ability()));
    }

    public PokemonDetail toDetail(PokeApiPokemon pokemon, PokeApiSpecies species, PokeApiEvolutionChain chain) {
        List<PokemonStat> stats = orEmpty(pokemon.stats()).stream()
                .map(stat -> new PokemonStat(stat.stat().name(), stat.baseStat()))
                .toList();
        return new PokemonDetail(
                pokemon.id(),
                pokemon.name(),
                imageOf(pokemon),
                spriteOf(pokemon),
                names(pokemon.types(), slot -> slot.type()),
                toKilograms(pokemon.weight()),
                pokemon.height() / 10.0,
                names(pokemon.abilities(), slot -> slot.ability()),
                stats,
                species == null ? null : englishGenus(species),
                species == null ? null : englishDescription(species),
                chain == null ? null : toEvolutionTree(chain));
    }

    public EvolutionNode toEvolutionTree(PokeApiEvolutionChain chain) {
        return toNode(chain.chain());
    }

    public int speciesIdOf(PokeApiPokemon pokemon) {
        return idFromUrl(pokemon.species() == null ? null : pokemon.species().url());
    }

    public Optional<Integer> chainIdOf(PokeApiSpecies species) {
        if (species.evolutionChain() == null || species.evolutionChain().url() == null) {
            return Optional.empty();
        }
        return Optional.of(idFromUrl(species.evolutionChain().url()));
    }

    public int idFromUrl(String url) {
        Matcher matcher = url == null ? null : TRAILING_ID.matcher(url);
        if (matcher == null || !matcher.find()) {
            throw new CatalogUnavailableException("PokeAPI returned an unexpected URL: " + url, false, null);
        }
        return Integer.parseInt(matcher.group(1));
    }

    private static double toKilograms(int hectograms) {
        return hectograms / 10.0;
    }

    private static String spriteOf(PokeApiPokemon pokemon) {
        return pokemon.sprites() == null ? null : pokemon.sprites().frontDefault();
    }

    private static String imageOf(PokeApiPokemon pokemon) {
        if (pokemon.sprites() != null && pokemon.sprites().other() != null
                && pokemon.sprites().other().officialArtwork() != null
                && pokemon.sprites().other().officialArtwork().frontDefault() != null) {
            return pokemon.sprites().other().officialArtwork().frontDefault();
        }
        return spriteOf(pokemon);
    }

    private static <S> List<String> names(List<S> slots, java.util.function.Function<S, NamedRef> ref) {
        return orEmpty(slots).stream().map(ref).filter(Objects::nonNull).map(NamedRef::name).toList();
    }

    private static String englishGenus(PokeApiSpecies species) {
        return orEmpty(species.genera()).stream()
                .filter(genus -> isEnglish(genus.language()))
                .map(PokeApiSpecies.Genus::genus)
                .findFirst()
                .orElse(null);
    }

    // Flavor texts come hard-wrapped with \n and \f; collapse them so the UI can wrap freely.
    private static String englishDescription(PokeApiSpecies species) {
        return orEmpty(species.flavorTextEntries()).stream()
                .filter(entry -> isEnglish(entry.language()) && entry.flavorText() != null)
                .map(entry -> entry.flavorText().replaceAll("\\s+", " ").strip())
                .findFirst()
                .orElse(null);
    }

    private static boolean isEnglish(NamedRef language) {
        return language != null && ENGLISH.equals(language.name());
    }

    private EvolutionNode toNode(ChainLink link) {
        int id = idFromUrl(link.species().url());
        List<String> requirements = orEmpty(link.evolutionDetails()).stream()
                .map(PokeApiMapper::describe)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        List<EvolutionNode> next = orEmpty(link.evolvesTo()).stream().map(this::toNode).toList();
        // SHORTCUT: node sprites follow PokeAPI's URL pattern instead of one extra call per node;
        // upgrade when the sprites host changes or exact per-node images are required.
        return new EvolutionNode(id, link.species().name(), SPRITE_BASE + id + ".png", requirements, next);
    }

    private static String describe(EvolutionDetail detail) {
        if (detail.trigger() == null || detail.trigger().name() == null) {
            return null;
        }
        String trigger = detail.trigger().name();
        return switch (trigger) {
            case "level-up" -> levelUp(detail);
            case "use-item" -> detail.item() == null ? "Use item" : "Use " + detail.item().name();
            default -> humanize(trigger);
        };
    }

    private static String levelUp(EvolutionDetail detail) {
        List<String> conditions = Stream.of(
                        detail.minLevel() == null ? null : "level " + detail.minLevel(),
                        detail.minHappiness() == null ? null : "happiness " + detail.minHappiness(),
                        detail.timeOfDay() == null || detail.timeOfDay().isBlank() ? null : detail.timeOfDay())
                .filter(Objects::nonNull)
                .toList();
        return conditions.isEmpty() ? "Level up" : "Level up (" + String.join(", ", conditions) + ")";
    }

    private static String humanize(String slug) {
        String words = slug.replace('-', ' ');
        return words.substring(0, 1).toUpperCase(Locale.ROOT) + words.substring(1);
    }

    private static <T> List<T> orEmpty(List<T> list) {
        return list == null ? List.of() : list;
    }
}
