package com.ballastlane.assessment.infrastructure.persistence;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** A trainer's entry in their collection: the link to a shared Pokemon plus the trainer's own notes. */
@Entity
@Table(name = "trainer_pokemon")
class TrainerPokemonEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;

    // Guards the notes: two edits of the same entry cannot silently overwrite each other.
    @Version
    Long version;

    // Plain column (no association) so loading an entry never loads a user.
    @Column(name = "user_id", nullable = false)
    Long userId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "pokemon_id", nullable = false)
    PokemonEntity pokemon;

    @Column(length = 50)
    String nickname;

    @Column(length = 50)
    String region;

    @ElementCollection
    @CollectionTable(name = "trainer_pokemon_tags", joinColumns = @JoinColumn(name = "trainer_pokemon_id"))
    @OrderColumn(name = "sort_order")
    @Column(name = "tag", nullable = false, length = 30)
    List<String> tags = new ArrayList<>();

    @Column(name = "added_at", nullable = false)
    Instant addedAt;
}
