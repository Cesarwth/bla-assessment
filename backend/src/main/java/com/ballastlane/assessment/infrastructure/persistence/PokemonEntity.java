package com.ballastlane.assessment.infrastructure.persistence;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** The shared catalog row: PokeAPI data only, no trainer-specific fields. Fields are package-private because {@link PokemonPersistenceMapper} is the sole user. */
@Entity
@Table(name = "pokemon")
class PokemonEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;

    @Version
    Long version;

    @Column(name = "pokeapi_id", nullable = false, unique = true)
    int pokeApiId;

    @Column(nullable = false, length = 100)
    String name;

    @Column(name = "sprite_url", length = 500)
    String spriteUrl;

    @Column(name = "image_url", length = 500)
    String imageUrl;

    @ElementCollection
    @CollectionTable(name = "pokemon_types", joinColumns = @JoinColumn(name = "pokemon_id"))
    @OrderColumn(name = "sort_order")
    @Column(name = "type_name", nullable = false, length = 50)
    List<String> types = new ArrayList<>();

    @ElementCollection
    @CollectionTable(name = "pokemon_abilities", joinColumns = @JoinColumn(name = "pokemon_id"))
    @OrderColumn(name = "sort_order")
    @Column(name = "ability_name", nullable = false, length = 100)
    List<String> abilities = new ArrayList<>();

    @ElementCollection
    @CollectionTable(name = "pokemon_stats", joinColumns = @JoinColumn(name = "pokemon_id"))
    @OrderColumn(name = "sort_order")
    List<StatEmbeddable> stats = new ArrayList<>();

    @Column(name = "weight_kg", nullable = false)
    double weightKg;

    @Column(name = "height_m", nullable = false)
    double heightM;

    @Column(length = 100)
    String genus;

    @Column(length = 2000)
    String description;

    @Column(name = "evolution_chain_json", length = 20000)
    String evolutionChainJson;

    @Column(name = "synced_at", nullable = false)
    Instant syncedAt;
}
